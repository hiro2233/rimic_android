/*
 * Copyright (C) 2015 Andrew Comminos <andrew@comminos.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package bo.htakey.rimic;

import bo.htakey.rimic.model.Server;
import bo.htakey.rimic.util.RimicDisconnectedException;
import bo.htakey.rimic.util.RimicException;
import bo.htakey.rimic.util.IRimicObserver;

/**
 * A public interface for clients to communicate with a {@link RimicService}.
 * The long-term goal for this class is to migrate of the complexity out of this class into a
 * RimicProtocol class that is owned by a {@link bo.htakey.rimic.net.RimicConnection}.
 * <br><br>
 * Calls are not guaranteed to be thread-safe, so only call the binder from the main thread.
 * Service state changes related to connection state are only guaranteed to work if isConnected()
 * is checked to be true.
 * <br><br>
 * If not explicitly stated in the method documentation, any call that depends on connection state
 * will throw IllegalStateException if disconnected or not synchronized.
 */
public interface IRimicService {
    void registerObserver(IRimicObserver observer);

    void unregisterObserver(IRimicObserver observer);

    /**
     * @return true if handshaking with the server has completed.
     */
    boolean isConnected();

    /**
     * Disconnects from the active connection, or does nothing if no connection is active.
     */
    void disconnect();

    /**
     * Returns the current connection state of the service.
     * @return one of {@link RimicService.ConnectionState}.
     */
    RimicService.ConnectionState getConnectionState();

    /**
     * If the {@link RimicService} disconnected due to an error, returns that error.
     * @return The error causing disconnection. If the last disconnection was successful or a
     *         connection has yet to be established, returns null.
     */
    RimicException getConnectionError();

    /**
     * Returns the reconnection state of the {@link RimicService}.
     * @return true if the service will attempt to automatically reconnect in the future.
     */
    boolean isReconnecting();

    /**
     * Cancels any future reconnection attempts. Does nothing if reconnection is not in progress.
     */
    void cancelReconnect();

    /**
     * @return the server that Rimic is currently connected to, was connected to, or will attempt connection to.
     */
    Server getTargetServer();

    /**
     * Returns the active session with the remote, or throws an exception if no session is currently
     * active. This can be checked using {@link IRimicService#isConnected()}.
     * @return the active session.
     * @throws RimicDisconnectedException if the connection state is not CONNECTED.
     */
    IRimicSession getSession() throws RimicDisconnectedException;
}
