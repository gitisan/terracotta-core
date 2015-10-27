/*
 *
 *  The contents of this file are subject to the Terracotta Public License Version
 *  2.0 (the "License"); You may not use this file except in compliance with the
 *  License. You may obtain a copy of the License at
 *
 *  http://terracotta.org/legal/terracotta-public-license.
 *
 *  Software distributed under the License is distributed on an "AS IS" basis,
 *  WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License for
 *  the specific language governing rights and limitations under the License.
 *
 *  The Covered Software is Terracotta Core.
 *
 *  The Initial Developer of the Covered Software is
 *  Terracotta, Inc., a Software AG company
 *
 */
package com.tc.management.remote.connect;

import com.tc.logging.TCLogger;
import com.tc.logging.TCLogging;
import com.tc.management.TerracottaManagement;
import com.tc.net.protocol.tcm.MessageChannel;
import com.tc.util.UUID;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import javax.management.MBeanServer;
import javax.management.MBeanServerConnection;
import javax.management.MBeanServerNotification;
import javax.management.Notification;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import javax.management.QueryExp;
import javax.management.remote.generic.ConnectionClosedException;

public class ClientBeanBag {
  private static final TCLogger       LOGGER    = TCLogging.getLogger(ClientBeanBag.class);

  private final Set<ObjectName>       beanNames = new HashSet<ObjectName>();
  private final MBeanServer           l2MBeanServer;
  private final MBeanServerConnection l1Connection;
  private final MessageChannel        channel;

  /**
   * Unique client identifier generated by the DSOClientConfigHelper, used to filter the set of tunneled MBeans. This
   * comes in on the L1ConnectionMessage.
   */
  private final UUID                  uuid;
  private String[]                    tunneledDomains;
  private QueryExp                    queryExp;
  private MBeanRegistrationListener   mbeanRegistrationListener;
  private RemoteRegistrationFilter    remoteRegistrationFilter;

  public ClientBeanBag(MBeanServer l2MBeanServer, MessageChannel channel, UUID uuid, String[] tunneledDomains,
                       MBeanServerConnection l1Connection) {
    this.l2MBeanServer = l2MBeanServer;
    this.channel = channel;
    this.uuid = uuid;
    this.l1Connection = l1Connection;

    setTunneledDomains(tunneledDomains);
  }

  public synchronized void unregisterBeans() {
    for (ObjectName name : beanNames) {
      unregisterBean(name, false);
    }
    beanNames.clear();
  }

  public synchronized String[] getTunneledDomains() {
    return tunneledDomains;
  }

  public synchronized void setTunneledDomains(String[] tunneledDomains) {
    this.tunneledDomains = tunneledDomains;
    this.queryExp = TerracottaManagement.matchAllTerracottaMBeans(uuid, tunneledDomains);
  }

  public UUID getUuid() {
    return uuid;
  }

  public synchronized boolean updateRegisteredBeans() throws IOException {
    ObjectName on = null;
    try {
      on = new ObjectName("JMImplementation:type=MBeanServerDelegate");
    } catch (Exception e) {
      LOGGER.error("Unable to construct the mbean object name referring to MBeanServerDelegate", e);
    }

    // if a listener was previous registered, first unregister it
    if (on != null) {
      try {
        if (mbeanRegistrationListener != null) {
          l1Connection.removeNotificationListener(on, mbeanRegistrationListener);
        }
      } catch (Exception e) {
        LOGGER.error("Unable to remove listener from MBeanServerDelegate", e);
      }

      // register as a listener before query'ing beans to avoid missing any registrations
      try {
        mbeanRegistrationListener = new MBeanRegistrationListener(this);
        remoteRegistrationFilter = new RemoteRegistrationFilter(queryExp);
        l1Connection.addNotificationListener(on, mbeanRegistrationListener, remoteRegistrationFilter, null);
      } catch (Exception e) {
        LOGGER.error("Unable to add listener to remove MBeanServerDelegate, no client MBeans "
                     + " registered after connect-time will be tunneled into the L2", e);
      }
    }

    // now that we're listening we can query and let the bean bag deal with the possible concurrency
    Set<ObjectName> mBeans = l1Connection.queryNames(null, queryExp);
    for (ObjectName objName : mBeans) {
      try {
        registerBean(objName);
      } catch (Exception e) {
        if (isConnectionException(e)) {
          LOGGER.warn("Client disconnected before all beans could be registered");
          unregisterBeans();
          return false;
        }
      }
    }

    return true;
  }

  private boolean isConnectionException(Throwable e) {
    while (e.getCause() != null) {
      e = e.getCause();
    }

    if (e instanceof ConnectionClosedException) { return true; }
    if ((e instanceof IOException) || ("The connection has been closed.".equals(e.getMessage()))) { return true; }

    return false;
  }

  synchronized void registerBean(ObjectName objName) {
    LOGGER.debug("registerBean: " + objName);

    ObjectName modifiedObjName = null;
    try {
      if (queryExp.apply(objName)) {
        modifiedObjName = TerracottaManagement.addNodeInfo(objName, channel.getRemoteAddress());
        if (beanNames.add(modifiedObjName)) {
          MBeanMirror mirror = MBeanMirrorFactory.newMBeanMirror(l1Connection, objName, modifiedObjName);
          l2MBeanServer.registerMBean(mirror, modifiedObjName);
          LOGGER.debug("Tunneled MBean '" + modifiedObjName + "'");
        }
      } else {
        LOGGER.info("Ignoring bean for registration: " + objName);
      }
    } catch (Exception e) {
      if (modifiedObjName != null) {
        beanNames.remove(modifiedObjName);
      }
      LOGGER.warn("Unable to register DSO client bean[" + objName + "] due to " + e.getMessage());
    }
  }

  synchronized void unregisterBean(ObjectName objName, boolean remove) {
    LOGGER.debug("unregisterBean: " + objName);

    ObjectName modifiedObjName = null;
    try {
      // only change the node attribute of the ObjectName if the MBeans has to be removed from the beanNames set
      // otherwise this means the beanNames set is being iterated and already contains correct ObjectName's
      modifiedObjName = remove ? TerracottaManagement.addNodeInfo(objName, channel.getRemoteAddress()) : objName;
      if (beanNames.contains(modifiedObjName)) {
        l2MBeanServer.unregisterMBean(modifiedObjName);
        LOGGER.debug("Unregistered Tunneled MBean '" + modifiedObjName + "'");
      } else {
        LOGGER.info("Ignoring bean for unregistration: " + objName);
      }
    } catch (Exception e) {
      LOGGER.warn("Unable to unregister DSO client bean[" + modifiedObjName + "]", e);
    } finally {
      if (remove && modifiedObjName != null) {
        beanNames.remove(modifiedObjName);
      }
    }
  }

  private static final class RemoteRegistrationFilter implements NotificationFilter {
    private static final long serialVersionUID = 6745130208320538044L;
    private final QueryExp    queryExp;

    private RemoteRegistrationFilter(QueryExp queryExp) {
      this.queryExp = queryExp;
    }

    @Override
    public boolean isNotificationEnabled(Notification notification) {
      if (notification instanceof MBeanServerNotification) {
        final MBeanServerNotification mbsn = (MBeanServerNotification) notification;
        final String notifType = mbsn.getType();
        if (notifType.equals(MBeanServerNotification.REGISTRATION_NOTIFICATION)
            || notifType.equals(MBeanServerNotification.UNREGISTRATION_NOTIFICATION)) {
          try {
            return queryExp.apply(mbsn.getMBeanName());
          } catch (Exception e) {
            LOGGER.warn("Unable to filter remote MBean registration", e);
            return false;
          }
        }
      }
      return false;
    }
  }

  private static final class MBeanRegistrationListener implements NotificationListener {
    private final ClientBeanBag bag;

    public MBeanRegistrationListener(ClientBeanBag bag) {
      this.bag = bag;
    }

    @Override
    final public void handleNotification(Notification notification, Object context) {
      if (notification instanceof MBeanServerNotification) {
        String type = notification.getType();
        MBeanServerNotification mbsn = (MBeanServerNotification) notification;
        ObjectName beanName = mbsn.getMBeanName();
        if (type.equals(MBeanServerNotification.REGISTRATION_NOTIFICATION)) {
          bag.registerBean(beanName);
        } else if (type.equals(MBeanServerNotification.UNREGISTRATION_NOTIFICATION)) {
          bag.unregisterBean(beanName, true);
        }
      }
    }
  }

}
