/*
 * This file is part of ***  M y C o R e  ***
 * See https://www.mycore.de/ for details.
 *
 * MyCoRe is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MyCoRe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with MyCoRe.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.mycore.lta.transport;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mycore.common.config.MCRConfigurationException;
import org.mycore.common.config.annotation.MCRProperty;
import org.mycore.lta.MCRLTAPackage;
import org.mycore.lta.MCRLTATransport;
import org.mycore.resource.MCRResourceResolver;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;

/**
 * Uploads packages via SFTP. The package of an object is uploaded to <code>{BaseDir}/{objectId}</code>, former
 * content of that directory is removed. One SSH session is used for all files of a package.
 * <p>
 * Properties:
 * <ul>
 *   <li><code>Host</code>, <code>Username</code>, <code>BaseDir</code>: the target</li>
 *   <li><code>Port</code>: the port, defaults to <code>MCR.LTA.Default.Sftp.Port</code></li>
 *   <li><code>KeyFile</code>: the private key, resolved with the resource resolver (e.g. a file in the
 *   configuration directory)</li>
 *   <li><code>Passphrase</code> (optional): the passphrase of the private key</li>
 *   <li><code>KnownHostsFile</code>: the known hosts file, resolved with the resource resolver</li>
 *   <li><code>ConnectTimeoutSeconds</code>: the connect timeout, defaults to
 *   <code>MCR.LTA.Default.Sftp.ConnectTimeoutSeconds</code></li>
 * </ul>
 */
public class MCRLTASftpTransport implements MCRLTATransport {

    private static final Logger LOGGER = LogManager.getLogger();

    private static final int SFTP_ERROR_NO_SUCH_FILE = 2;

    private static final Set<String> VIRTUAL_DIRECTORY_NAMES = Set.of(".", "..");

    /**
     * The host.
     */
    @MCRProperty(name = "Host")
    public String host;

    /**
     * The port.
     */
    @MCRProperty(name = "Port", defaultName = "MCR.LTA.Default.Sftp.Port")
    public String port;

    /**
     * The user name.
     */
    @MCRProperty(name = "Username")
    public String username;

    /**
     * The base directory on the server.
     */
    @MCRProperty(name = "BaseDir")
    public String baseDir;

    /**
     * The resource path of the private key.
     */
    @MCRProperty(name = "KeyFile")
    public String keyFile;

    /**
     * The passphrase of the private key, may be null.
     */
    @MCRProperty(name = "Passphrase", required = false)
    public String passphrase;

    /**
     * The resource path of the known hosts file.
     */
    @MCRProperty(name = "KnownHostsFile")
    public String knownHostsFile;

    /**
     * The connect timeout in seconds.
     */
    @MCRProperty(name = "ConnectTimeoutSeconds", defaultName = "MCR.LTA.Default.Sftp.ConnectTimeoutSeconds")
    public String connectTimeoutSeconds;

    @Override
    public String upload(MCRLTAPackage pkg) throws IOException {
        String subdirectory = pkg.objectId().toString();
        String target = stripTrailingSlash(baseDir) + "/" + subdirectory;
        Session session = null;
        ChannelSftp channel = null;
        try {
            session = connect();
            channel = (ChannelSftp) session.openChannel("sftp");
            channel.connect(timeoutMillis());
            ensureDirectory(channel, target);
            clearDirectory(channel, target);
            for (MCRLTAPackage.Entry entry : pkg.entries()) {
                String path = target + "/" + entry.path();
                ensureDirectory(channel, path.substring(0, path.lastIndexOf('/')));
                LOGGER.debug("Uploading {}", path);
                try (InputStream in = entry.open()) {
                    channel.put(in, path);
                }
            }
            LOGGER.info("Uploaded {} files of {} to {}:{}", pkg.entries().size(), subdirectory, host, target);
            return subdirectory;
        } catch (JSchException | SftpException e) {
            throw new IOException("SFTP upload of " + subdirectory + " to " + host + " failed", e);
        } finally {
            if (channel != null) {
                channel.disconnect();
            }
            if (session != null) {
                session.disconnect();
            }
        }
    }

    private Session connect() throws JSchException {
        JSch jsch = new JSch();
        byte[] passphraseBytes = passphrase == null || passphrase.isEmpty() ? null
            : passphrase.getBytes(StandardCharsets.UTF_8);
        jsch.addIdentity(keyFile, readResource(keyFile), null, passphraseBytes);
        try (InputStream knownHosts = openResource(knownHostsFile)) {
            jsch.setKnownHosts(knownHosts);
        } catch (IOException e) {
            throw new MCRConfigurationException("Unable to read " + knownHostsFile, e);
        }
        Session session = jsch.getSession(username, host, Integer.parseInt(port.trim()));
        session.setConfig("StrictHostKeyChecking", "yes");
        LOGGER.debug("Connecting to {}@{}:{}", username, host, port);
        session.connect(timeoutMillis());
        return session;
    }

    private int timeoutMillis() {
        return Integer.parseInt(connectTimeoutSeconds.trim()) * 1000;
    }

    private static byte[] readResource(String path) {
        try (InputStream in = openResource(path)) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new MCRConfigurationException("Unable to read " + path, e);
        }
    }

    private static InputStream openResource(String path) throws IOException {
        URL url = MCRResourceResolver.obtainInstance().resolveResource(path)
            .orElseThrow(() -> new MCRConfigurationException("Configured resource not found: " + path));
        return url.openStream();
    }

    private static String stripTrailingSlash(String path) {
        return path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    }

    private static boolean exists(ChannelSftp channel, String path) throws SftpException {
        try {
            channel.stat(path);
            return true;
        } catch (SftpException e) {
            if (e.id == SFTP_ERROR_NO_SUCH_FILE) {
                return false;
            }
            throw e;
        }
    }

    private static void ensureDirectory(ChannelSftp channel, String path) throws SftpException {
        if (exists(channel, path)) {
            return;
        }
        StringBuilder partialPath = new StringBuilder();
        for (String part : path.split("/")) {
            if (part.isEmpty()) {
                continue;
            }
            partialPath.append('/').append(part);
            String current = path.startsWith("/") ? partialPath.toString() : partialPath.substring(1);
            if (!exists(channel, current)) {
                LOGGER.debug("Creating remote directory {}", current);
                channel.mkdir(current);
            }
        }
    }

    private static void clearDirectory(ChannelSftp channel, String path) throws SftpException {
        List<ChannelSftp.LsEntry> entries = List.copyOf(channel.ls(path));
        for (ChannelSftp.LsEntry entry : entries) {
            if (VIRTUAL_DIRECTORY_NAMES.contains(entry.getFilename())) {
                continue;
            }
            String entryPath = path + "/" + entry.getFilename();
            if (entry.getAttrs().isDir()) {
                clearDirectory(channel, entryPath);
                channel.rmdir(entryPath);
            } else {
                channel.rm(entryPath);
            }
        }
    }

}
