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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mycore.common.config.annotation.MCRProperty;
import org.mycore.lta.MCRLTAPackage;
import org.mycore.lta.MCRLTATransport;

/**
 * Writes packages to a local directory, e.g. a mounted share or for tests. The package of an object is written to
 * <code>{BaseDir}/{objectId}</code>, former content is removed.
 * <p>
 * Properties:
 * <ul>
 *   <li><code>BaseDir</code>: the base directory</li>
 * </ul>
 */
public class MCRLTAFileSystemTransport implements MCRLTATransport {

    private static final Logger LOGGER = LogManager.getLogger();

    /**
     * The base directory.
     */
    @MCRProperty(name = "BaseDir")
    public String baseDir;

    @Override
    public String upload(MCRLTAPackage pkg) throws IOException {
        String subdirectory = pkg.objectId().toString();
        Path target = Path.of(baseDir).resolve(subdirectory).normalize();
        clear(target);
        for (MCRLTAPackage.Entry entry : pkg.entries()) {
            Path file = target.resolve(entry.path()).normalize();
            if (!file.startsWith(target)) {
                throw new IOException("Path of entry leaves the package: " + entry.path());
            }
            Files.createDirectories(file.getParent());
            try (InputStream in = entry.open()) {
                Files.copy(in, file, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        LOGGER.info("Wrote package of {} to {}", subdirectory, target);
        return subdirectory;
    }

    private static void clear(Path target) throws IOException {
        if (Files.exists(target)) {
            try (Stream<Path> stream = Files.walk(target)) {
                for (Path path : stream.sorted(Comparator.reverseOrder()).toList()) {
                    if (!path.equals(target)) {
                        Files.delete(path);
                    }
                }
            }
        }
        Files.createDirectories(target);
    }

}
