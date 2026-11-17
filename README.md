# MyCoRe LTA Plugin

A long term archive (LTA) plugin for [MyCoRe](https://www.mycore.de/) and MIR applications. It submits objects to a
long term archive, tracks the state of each submission and verifies the archived entities by reports of the archive.
The bundled implementation targets [Ex Libris Rosetta](https://exlibrisgroup.com/products/rosetta-digital-preservation-system/)
as operated by the TIB, but every component is a configurable instance and can be replaced.

Compatible with MyCoRe 2025.06 and Java 21. The Dublin Core stylesheet imports `mods2oai_dc.xsl` of MIR.

## Principles

- One MyCoRe object is one intellectual entity (IE) in the archive. Archived objects are never updated. A new version
  is a new object that refers to its predecessor in MODS (`mods:relatedItem` of type `original` or `otherVersion`).
- Submissions are triggered only by a cronjob or by commands, never by saving an object.
- The state of each submission attempt is stored as service flag of type `MyCoRe-LTA` (JSON) at the object. The
  database table `MCRLTASubmission` is a copy for queries and can be rebuilt with `lta sync all objects`.
- At most one submission per object and service is open or successful. Only failed attempts lead to further flags.

## How it works

| Step   | Trigger                                   | Result                                                       |
|--------|-------------------------------------------|--------------------------------------------------------------|
| Submit | `MCRLTASubmitCronjob` or `lta submit ...` | package uploaded, deposit created, state `SUBMITTED`         |
| Check  | `MCRLTACheckCronjob` or `lta check ...`   | deposit status queried, final state `FINISHED` or `FAILED`   |
| Verify | `lta verify service ... with report ...`  | IE PID taken from the report of the archive, state `VERIFIED` |

```
MCRLTAService (one per service, e.g. "TIB")
├── Selector            candidates (object IDs)
├── PackageGenerator    builds the package (MCRRosettaPackageGenerator)
│   ├── DerivateFilters    which derivates
│   ├── FileFilters        which files
│   ├── ObjectTransformer  object → dc.xml
│   └── ManifestTransformer manifest → content/ie1.xml (METS)
├── Transport           uploads the package (SFTP or file system)
├── Client              creates the deposit and queries its status (Rosetta deposit API)
└── ReportImporter      reads CSV or XLSX reports of the archive (optional)
```

If the package can not be built (e.g. no Rosetta profile matches), the attempt is stored as `FAILED` and the run
continues. If upload or deposit fail, nothing is stored and the run is aborted, because these errors usually affect
all objects. Failed submissions are never resubmitted automatically: the default query excludes every object with a
flag of the service (`-lta.TIB.state:*`). Once the cause is fixed, submit the object explicitly with
`lta submit object {id} to service {service}`.

## Installation

Add the JAR to the `lib` directory of the MyCoRe home of your application. The JAR is self contained: JSch
(BSD license) and Apache Commons CSV (Apache License 2.0) are included and relocated to `org.mycore.lta.shaded`, see
`META-INF/licenses`. All other dependencies are provided by MyCoRe.

The plugin registers its event handler, URI resolver `lta:`, commands, JPA mapping, Solr fields (`lta.{service}.*`)
and stylesheets (`lta-rosetta-dc`, `lta-rosetta-mets`) by itself. After the first start, update the Solr schema
(`reload solr configuration main in core main`) and reindex, so the selector can exclude submitted objects.

## Configuration

All defaults are documented in [`mycore.properties`](src/main/resources/config/mcr-lta-plugin/mycore.properties),
which also contains a complete, commented example for the TIB. A service is configured below
`MCR.LTA.Service.{id}`, the cronjobs below `MCR.Cronjob.Jobs.{id}`.

| Property                                 | Default              | Description                                                |
|------------------------------------------|----------------------|------------------------------------------------------------|
| `MCR.LTA.Default.Reason`                 | `Initial submission` | Reason of a submission without explicit reason             |
| `MCR.LTA.Default.PauseSeconds`           | `2`                  | Pause between two submissions of a cronjob run or a bulk command |
| `MCR.LTA.Default.Submit.Limit`           | `100`                | Maximum number of submissions per run of the submit cronjob |
| `MCR.LTA.Default.Check.IntervalHours`    | `24`                 | Deposits checked within this interval are skipped          |
| `MCR.LTA.Default.Check.Limit`            | `500`                | Maximum number of deposits per run of the check cronjob    |
| `MCR.LTA.Default.Rosetta.PendingStates`  | `INPROCESS`          | Deposit states that mean "still processing"                |
| `MCR.LTA.Default.Rosetta.SuccessStates`  | `FINISHED,APPROVED`  | Deposit states that mean success, all others mean failure  |

The selector selects candidates without any restriction by date:

```properties
MCR.LTA.Service.TIB.Selector.Class=org.mycore.lta.selector.MCRLTASolrSelector
MCR.LTA.Service.TIB.Selector.Query=state:published AND -lta.TIB.state:*
```

Before each submission, the service checks the database again, so objects with an open or successful submission are
never submitted twice, even if the Solr index is outdated.

## Initial import

When a service is set up for an existing repository, thousands of objects are candidates at once. The submit cronjob
handles only `Limit` objects per run, which is meant for the daily increase. For the initial import:

1. Agree on the schedule with the archive (the TIB), in particular the number of packages per day and the time of
   day. Start with a small test batch: `lta submit 10 candidates to service TIB`.
2. Disable the submit cronjob (`MCR.Cronjob.Jobs.LTASubmitTIB.Enabled=false`) during the import.
3. Run `lta submit all candidates to service TIB` or, in portions, `lta submit 500 candidates to service TIB` in the
   command line interface. These commands only select the candidates and queue one command
   `lta submit candidate {id} to service TIB` per object. Each object is submitted in its own transaction, followed by
   the pause of `PauseSeconds` to protect the archive.
4. The import can be resumed at any time: queued commands skip objects that are no longer candidates, and a new
   `lta submit all candidates` selects only the remaining ones. If upload or deposit fail, the remaining commands are
   not executed; after fixing the cause, the same command continues where the import stopped.
5. The check cronjob handles at most `Limit` open deposits per run, the ones checked longest ago first. After the
   import, `lta check service TIB` checks all open deposits at once.
6. Enable the submit cronjob again for the daily increase.

## Commands

| Command | Description |
|---------|-------------|
| `lta submit object {id} to service {service} [with reason {reason}]` | Submits one object, fails if it has an open or successful submission |
| `lta submit all candidates to service {service}` | Queues one submit command per candidate |
| `lta submit {n} candidates to service {service}` | Queues one submit command for up to `n` candidates |
| `lta submit candidate {id} to service {service}` | Submits a candidate, skips non candidates, pauses afterwards |
| `lta check service {service}` | Queues one check command per open deposit |
| `lta check object {id} for service {service}` | Checks the open deposits of an object |
| `lta check deposit {deposit} of object {id} for service {service}` | Checks one deposit |
| `lta verify service {service} with report {file}` | Reads a CSV or XLSX report and queues one verify command per row |
| `lta sync object {id}` | Rebuilds the database entries of an object from its flags |
| `lta sync all objects` | Rebuilds the database entries of all objects |
| `lta list submissions of object {id}` | Lists the submissions of an object |
| `lta show statistics of service {service}` | Shows the number of submissions per state |

Rows of a report that can not be assigned to a submission are logged as problems, never ignored silently.

## Build

```bash
mvn clean install
```
