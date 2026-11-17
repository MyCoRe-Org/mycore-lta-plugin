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

package org.mycore.lta;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import org.mycore.datamodel.metadata.MCRObjectID;

/**
 * A package to be uploaded to a long term archiving service.
 *
 * @param objectId the ID of the archived object
 * @param manifest the manifest of the package
 * @param entries the files of the package
 */
public record MCRLTAPackage(MCRObjectID objectId, MCRLTAManifest manifest, List<Entry> entries) {

    /**
     * Creates a new package.
     *
     * @param objectId the ID of the archived object
     * @param manifest the manifest of the package
     * @param entries the files of the package
     */
    public MCRLTAPackage {
        Objects.requireNonNull(objectId, "objectId must not be null");
        Objects.requireNonNull(manifest, "manifest must not be null");
        entries = List.copyOf(entries);
    }

    /**
     * A file of the package.
     */
    public interface Entry {

        /**
         * @return the path in the package, separated by slashes, without leading slash
         */
        String path();

        /**
         * @return the size in bytes
         * @throws IOException if the size can not be determined
         */
        long size() throws IOException;

        /**
         * @return a new stream of the content
         * @throws IOException if the content can not be read
         */
        InputStream open() throws IOException;

        /**
         * Creates an entry for a file, e.g. a file of a derivate.
         *
         * @param path the path in the package
         * @param file the file
         * @return the entry
         */
        static Entry ofFile(String path, Path file) {
            return new FileEntry(path, file);
        }

        /**
         * Creates an entry for generated content, e.g. a METS document.
         *
         * @param path the path in the package
         * @param content the content
         * @return the entry
         */
        static Entry ofBytes(String path, byte[] content) {
            return new BytesEntry(path, content.clone());
        }

    }

    private record FileEntry(String path, Path file) implements Entry {

        @Override
        public long size() throws IOException {
            return Files.size(file);
        }

        @Override
        public InputStream open() throws IOException {
            return Files.newInputStream(file);
        }

    }

    private record BytesEntry(String path, byte[] content) implements Entry {

        @Override
        public long size() {
            return content.length;
        }

        @Override
        public InputStream open() {
            return new java.io.ByteArrayInputStream(content);
        }

    }

}
