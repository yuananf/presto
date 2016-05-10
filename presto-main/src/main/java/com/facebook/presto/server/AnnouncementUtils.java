/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.facebook.presto.server;

import com.facebook.presto.execution.scheduler.NodeSchedulerConfig;
import com.facebook.presto.metadata.Metadata;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import io.airlift.discovery.client.Announcer;
import io.airlift.discovery.client.ServiceAnnouncement;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.google.common.base.Strings.nullToEmpty;
import static io.airlift.discovery.client.ServiceAnnouncement.ServiceAnnouncementBuilder;
import static io.airlift.discovery.client.ServiceAnnouncement.serviceAnnouncement;

public final class AnnouncementUtils
{
    private AnnouncementUtils() {}

    public static void updateDatasources(Announcer announcer, Metadata metadata, ServerConfig serverConfig, NodeSchedulerConfig schedulerConfig)
    {
        // get existing announcement
        ServiceAnnouncement announcement = getPrestoAnnouncement(announcer.getServiceAnnouncements());

        // get existing sources
        String property = nullToEmpty(announcement.getProperties().get("datasources"));
        List<String> values = Splitter.on(',').trimResults().omitEmptyStrings().splitToList(property);
        Set<String> datasources = new LinkedHashSet<>(values);

        // automatically build sources if not configured
        if (datasources.isEmpty()) {
            Set<String> catalogs = metadata.getCatalogNames().keySet();
            // if this is a dedicated coordinator, only add jmx
            if (serverConfig.isCoordinator() && !schedulerConfig.isIncludeCoordinator()) {
                if (catalogs.contains("jmx")) {
                    datasources.add("jmx");
                }
            }
            else {
                datasources.addAll(catalogs);
            }
        }

        // build announcement with updated sources
        ServiceAnnouncementBuilder builder = serviceAnnouncement(announcement.getType());
        for (Map.Entry<String, String> entry : announcement.getProperties().entrySet()) {
            if (!entry.getKey().equals("datasources")) {
                builder.addProperty(entry.getKey(), entry.getValue());
            }
        }
        builder.addProperty("datasources", Joiner.on(',').join(datasources));

        // update announcement
        announcer.removeServiceAnnouncement(announcement.getId());
        announcer.addServiceAnnouncement(builder.build());
    }

    public static void updateDatasourcesAnnouncement(Announcer announcer, String connectorId)
    {
        // get existing announcement
        ServiceAnnouncement announcement = getPrestoAnnouncement(announcer.getServiceAnnouncements());

        // update datasources property
        Map<String, String> properties = new LinkedHashMap<>(announcement.getProperties());
        String property = nullToEmpty(properties.get("datasources"));
        Set<String> datasources = new LinkedHashSet<>(Splitter.on(',').trimResults().omitEmptyStrings().splitToList(property));
        datasources.add(connectorId);
        properties.put("datasources", Joiner.on(',').join(datasources));

        // update announcement
        announcer.removeServiceAnnouncement(announcement.getId());
        announcer.addServiceAnnouncement(serviceAnnouncement(announcement.getType()).addProperties(properties).build());
        announcer.forceAnnounce();
    }

    private static ServiceAnnouncement getPrestoAnnouncement(Set<ServiceAnnouncement> announcements)
    {
        for (ServiceAnnouncement announcement : announcements) {
            if (announcement.getType().equals("presto")) {
                return announcement;
            }
        }
        throw new IllegalArgumentException("Presto announcement not found: " + announcements);
    }
}
