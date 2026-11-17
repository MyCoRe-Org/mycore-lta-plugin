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

package org.mycore.lta.filter;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.List;

import org.mycore.common.config.MCRConfiguration2;
import org.mycore.common.config.annotation.MCRProperty;
import org.mycore.datamodel.metadata.MCRDerivate;

/**
 * Accepts files by glob patterns. Patterns without a slash are matched against the file name, patterns with a
 * slash against the path in the derivate (starting with a slash).
 * <p>
 * Properties:
 * <ul>
 *   <li><code>Include</code> (optional): comma separated list of patterns, if set only matching files are
 *   accepted</li>
 *   <li><code>Exclude</code> (optional): comma separated list of patterns, matching files are rejected</li>
 * </ul>
 */
public class MCRLTAFileNameFilter implements MCRLTAFileFilter {

    private List<Pattern> include = List.of();

    private List<Pattern> exclude = List.of();

    /**
     * Sets the patterns of accepted files.
     *
     * @param value comma separated list of glob patterns
     */
    @MCRProperty(name = "Include", required = false)
    public void setInclude(String value) {
        this.include = parse(value);
    }

    /**
     * Sets the patterns of rejected files.
     *
     * @param value comma separated list of glob patterns
     */
    @MCRProperty(name = "Exclude", required = false)
    public void setExclude(String value) {
        this.exclude = parse(value);
    }

    private static List<Pattern> parse(String value) {
        return MCRConfiguration2.splitValue(value).map(Pattern::of).toList();
    }

    @Override
    public boolean accept(MCRDerivate derivate, String path) {
        if (!include.isEmpty() && include.stream().noneMatch(p -> p.matches(path))) {
            return false;
        }
        return exclude.stream().noneMatch(p -> p.matches(path));
    }

    private record Pattern(PathMatcher matcher, boolean fullPath) {

        static Pattern of(String glob) {
            return new Pattern(FileSystems.getDefault().getPathMatcher("glob:" + glob), glob.contains("/"));
        }

        boolean matches(String path) {
            if (fullPath) {
                return matcher.matches(Path.of(path));
            }
            String name = path.substring(path.lastIndexOf('/') + 1);
            return !name.isEmpty() && matcher.matches(Path.of(name));
        }

    }

}
