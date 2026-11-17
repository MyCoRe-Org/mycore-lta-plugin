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

package org.mycore.lta.cronjob;

import java.util.concurrent.Callable;

import org.mycore.common.MCRException;
import org.mycore.common.MCRSystemUserInformation;
import org.mycore.util.concurrent.MCRFixedUserCallable;

/**
 * Helper of the cronjobs. Cronjobs run without a session, so every unit of work gets its own session and
 * transaction as super user.
 */
final class MCRLTACronjobSupport {

    private MCRLTACronjobSupport() {
    }

    /**
     * Calls the given callable in a new session and transaction as super user.
     *
     * @param callable the unit of work
     * @param <V> the type of the result
     * @return the result
     * @throws Exception if the callable fails
     */
    static <V> V call(Callable<V> callable) throws Exception {
        return new MCRFixedUserCallable<>(callable, MCRSystemUserInformation.SUPER_USER).call();
    }

    /**
     * Calls the given callable in a new session and transaction as super user and wraps checked exceptions.
     *
     * @param callable the unit of work
     * @param <V> the type of the result
     * @return the result
     */
    static <V> V callUnchecked(Callable<V> callable) {
        try {
            return call(callable);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new MCRException(e);
        }
    }

}
