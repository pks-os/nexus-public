/*
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2008-present Sonatype, Inc.
 * All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
 * which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.
 *
 * Sonatype Nexus (TM) Professional Version is available from Sonatype, Inc. "Sonatype" and "Sonatype Nexus" are trademarks
 * of Sonatype, Inc. Apache Maven is a trademark of the Apache Software Foundation. M2eclipse is a trademark of the
 * Eclipse Foundation. All other trademarks are the property of their respective owners.
 */
package org.sonatype.nexus.repository.content.search.table;

import java.io.IOException;
import java.time.Duration;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.sonatype.goodies.common.ComponentSupport;
import org.sonatype.nexus.common.cooperation2.Cooperation2;
import org.sonatype.nexus.common.cooperation2.Cooperation2Factory;
import org.sonatype.nexus.common.entity.EntityId;
import org.sonatype.nexus.repository.Repository;
import org.sonatype.nexus.repository.content.facet.ContentFacet;
import org.sonatype.nexus.repository.content.fluent.FluentComponent;
import org.sonatype.nexus.repository.content.search.SearchEventHandler;
import org.sonatype.nexus.repository.content.store.InternalIds;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.sonatype.nexus.repository.content.store.InternalIds.toInternalId;

/**
 * This class is for incrementally updating the component_search table in response to ContentStoreEvents.
 *
 * It is also used for batch updating of the component_search table via the search rebuild task
 *
 * Re-indexes or deletes a component record in the search table. This class uses cooperation to make sure
 * that only one thread (within a node or across the nodes in a cluster) can re-index a specific component in
 * the search table. Deletes don't need to use cooperation because the re-index SQL always checks the existence
 * of the component before updating.
 *
 * @see SearchEventHandler
 * @see SqlSearchEventHandler
 */
@Named
@Singleton
public class SqlSearchIndexService
    extends ComponentSupport
{
  private final SearchTableDataProducer searchTableDataProducer;

  private final SearchTableStore searchStore;

  private final Cooperation2 cooperation2;

  @Inject
  public SqlSearchIndexService(
      final SearchTableDataProducer searchTableDataProducer,
      final SearchTableStore searchStore,
      final Cooperation2Factory cooperationFactory,
      @Named("${nexus.datastore.table.search.cooperation.majorTimeout:-0s}") final Duration majorTimeout,
      @Named("${nexus.datastore.table.search.cooperation.minorTimeout:-30s}") final Duration minorTimeout,
      @Named("${nexus.datastore.table.search.cooperation.threadsPerKey:-100}") final int threadsPerKey)
  {
    checkArgument(minorTimeout.getSeconds() >= 0, "Must use a non-negative timeout");
    this.searchTableDataProducer = checkNotNull(searchTableDataProducer);
    this.searchStore = checkNotNull(searchStore);
    this.cooperation2 = checkNotNull(cooperationFactory).configure()
        .majorTimeout(majorTimeout)
        .minorTimeout(minorTimeout)
        .threadsPerKey(threadsPerKey)
        .build(SqlSearchIndexService.class.getSimpleName());
  }

  public void indexBatch(final Collection<FluentComponent> components, final Repository repository) {
    log.debug("Saving batch of components for repository: {}", repository.getName());

    //This is a batch update not an incremental update and only
    // one instance of the RebuildIndexTask can be running in a HA cluster.
    searchStore.saveBatch(components.stream()
        .map(component -> searchTableDataProducer.createSearchTableData(component, repository))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .collect(toList()));
  }

  public void purge(final Collection<EntityId> componentIds, final Repository repository) {
    //We don't need cooperation for this because the SQL for SearchTableDao.save checks for the existence
    //of the component before saving. This solves any race conditions between DELETES and INSERT/UPDATES
    ContentFacet facet = repository.facet(ContentFacet.class);
    Set<Integer> internalIds = componentIds.stream().map(InternalIds::toInternalId).collect(toSet());
    log.debug("Purging indexes for component ids: {} and repository: {}", componentIds, repository.getName());
    searchStore.deleteComponentIds(facet.contentRepositoryId(), internalIds, repository.getFormat().getValue());
  }

  public void index(final Collection<EntityId> componentIds, final Repository repository) {
    componentIds.forEach(componentId -> indexSearchData(componentId, repository));
  }

  private void indexSearchData(final EntityId componentId, final Repository repository) {
    try {
      log.debug("Indexing component id: {}, repository: {}", componentId, repository.getName());
      cooperation2.on(() -> refreshComponentData(componentId, repository))
          .checkFunction(Optional::empty)
          .cooperate(cooperationKey(componentId, repository));
    }
    catch (IOException ex) {
      throw new SearchTableDataRefreshException(ex);
    }
    catch (ComponentNotFoundException ex) {
      log.debug("Skipping refresh because: {}", ex.getMessage());
    }
  }

  private Optional<SearchTableData> refreshComponentData(final EntityId componentId, final Repository repository) {
    FluentComponent componentFromDb = fetchComponentFromDb(componentId, repository);
    return ofNullable(
        searchTableDataProducer.createSearchTableData(componentFromDb, repository)
            .map(this::saveToStore)
            .orElseGet(() -> {
              searchStore.delete(
                  repository.facet(ContentFacet.class).contentRepositoryId(),
                  toInternalId(componentId),
                  repository.getFormat().getValue()
              );
              return null;
            })
    );
  }

  private String cooperationKey(final EntityId componentId, final Repository repository) {
    String format = repository.getFormat().getValue();
    return String.format("refresh-%s-%s-%d", repository.getName(), format, toInternalId(componentId));
  }

  private FluentComponent fetchComponentFromDb(final EntityId componentId, final Repository repository) {
    return repository.facet(ContentFacet.class)
        .components()
        .find(componentId)
        .orElseThrow(() -> new ComponentNotFoundException(
            String.format("Component %d not found for repository %s and format %s",
                toInternalId(componentId), repository.getName(), repository.getFormat().getValue())));
  }

  private SearchTableData saveToStore(final SearchTableData data)
  {
    log.debug("Saving {} to component_search table", data);
    searchStore.save(data);
    return data;
  }
}
