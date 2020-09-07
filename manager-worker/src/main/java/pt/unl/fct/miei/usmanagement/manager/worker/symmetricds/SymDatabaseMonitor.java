/*
 * MIT License
 *
 * Copyright (c) 2020 manager
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package pt.unl.fct.miei.usmanagement.manager.worker.symmetricds;

import lombok.extern.slf4j.Slf4j;
import org.jumpmind.db.model.Table;
import org.jumpmind.symmetric.common.Constants;
import org.jumpmind.symmetric.io.data.CsvData;
import org.jumpmind.symmetric.io.data.DataContext;
import org.jumpmind.symmetric.io.data.writer.DatabaseWriterFilterAdapter;
import org.jumpmind.symmetric.io.data.writer.IDatabaseWriterErrorHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import pt.unl.fct.miei.usmanagement.manager.database.hosts.cloud.CloudHostEntity;
import pt.unl.fct.miei.usmanagement.manager.database.hosts.edge.EdgeHostEntity;
import pt.unl.fct.miei.usmanagement.manager.worker.exceptions.EntityNotFoundException;
import pt.unl.fct.miei.usmanagement.manager.worker.exceptions.WorkerManagerException;
import pt.unl.fct.miei.usmanagement.manager.worker.management.docker.swarm.nodes.NodeRole;
import pt.unl.fct.miei.usmanagement.manager.worker.management.hosts.HostsService;
import pt.unl.fct.miei.usmanagement.manager.worker.management.hosts.cloud.CloudHostsService;
import pt.unl.fct.miei.usmanagement.manager.worker.management.hosts.edge.EdgeHostsService;

import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
class SymDatabaseMonitor extends DatabaseWriterFilterAdapter implements IDatabaseWriterErrorHandler {

	private final HostsService hostsService;
	private final CloudHostsService cloudHostsService;
	private CloudHostEntity oldCloudHost;
	private final EdgeHostsService edgeHostsService;
	private EdgeHostEntity oldEdgeHost;

	@Value("${external-id}")
	private String id;

	SymDatabaseMonitor(HostsService hostsService, CloudHostsService cloudHostsService,
					   EdgeHostsService edgeHostsService) {
		this.hostsService = hostsService;
		this.cloudHostsService = cloudHostsService;
		this.edgeHostsService = edgeHostsService;
	}

	@Override
	public boolean beforeWrite(DataContext context, Table table, CsvData data) {
		final String channelId = context.getBatch().getChannelId();
		if (channelId.equals(Constants.CHANNEL_RELOAD) || channelId.equals(Constants.CHANNEL_DEFAULT)) {
			final String tableName = table.getName();
			if ("cloud_hosts".equalsIgnoreCase(tableName)) {
				final Map<String, String> newCloudHost = data.toColumnNameValuePairs(table.getColumnNames(), CsvData.ROW_DATA);
				final Long id = Long.valueOf(newCloudHost.get("ID"));
				try {
					oldCloudHost = cloudHostsService.getCloudHostById(id);
				}
				catch (EntityNotFoundException e) {
					System.out.println(e.getMessage());
				}
			}
			else if ("edge_hosts".equalsIgnoreCase(tableName)) {
				final Map<String, String> newEdgeHost = data.toColumnNameValuePairs(table.getColumnNames(), CsvData.ROW_DATA);
				final Long id = Long.valueOf(newEdgeHost.get("ID"));
				oldEdgeHost = edgeHostsService.getEdgeHostById(id);
			}
		}
		return true;
	}

	@Override
	public void afterWrite(DataContext context, Table table, CsvData data) {
		final String channelId = context.getBatch().getChannelId();
		if (channelId.equals(Constants.CHANNEL_RELOAD) || channelId.equals(Constants.CHANNEL_DEFAULT)) {
			final String tableName = table.getName();
			if ("cloud_hosts".equalsIgnoreCase(tableName)) {
				final Map<String, String> newCloudHost = data.toColumnNameValuePairs(table.getColumnNames(), CsvData.ROW_DATA);
				final String oldWorkerId = oldCloudHost == null
					? null
					: (oldCloudHost.getManagedByWorker() == null ? null : oldCloudHost.getManagedByWorker().getId());
				final String newWorkerId = newCloudHost.get("MANAGED_BY_WORKER_ID");
				final String publicIpAddress = newCloudHost.get("PUBLIC_IP_ADDRESS");
				log.info("Inserted a cloud host {}: {} -> {} ({})", publicIpAddress, oldWorkerId, newWorkerId, id);
				if (publicIpAddress != null) {
					// cloud host is running
					if (Objects.equals(id, newWorkerId)) {
						// is a cloud host managed by this worker
						if (!Objects.equals(id, oldWorkerId)) {
							// is a newly added cloud host
							String privateIpAddress = newCloudHost.get("PRIVATE_IP_ADDRESS");
							hostsService.setupHost(publicIpAddress, privateIpAddress, NodeRole.WORKER);
						}
					}
					else if (Objects.equals(id, oldWorkerId)) {
						// is not a cloud host managed by this worker, but used to be
						hostsService.removeHost(publicIpAddress);
					}
				}
			}
      /*if ("edge_hosts".equalsIgnoreCase(tableName)) {

      }*/
		}
	}

	@Override
	public boolean handleError(DataContext context, Table table, CsvData data, Exception ex) {
		throw new WorkerManagerException(ex);
	}

}
