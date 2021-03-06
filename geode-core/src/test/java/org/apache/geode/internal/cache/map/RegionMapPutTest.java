/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.geode.internal.cache.map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import org.apache.geode.cache.Operation;
import org.apache.geode.cache.Scope;
import org.apache.geode.internal.cache.CachePerfStats;
import org.apache.geode.internal.cache.EntryEventImpl;
import org.apache.geode.internal.cache.EntryEventSerialization;
import org.apache.geode.internal.cache.InternalRegion;
import org.apache.geode.internal.cache.RegionClearedException;
import org.apache.geode.internal.cache.RegionEntry;
import org.apache.geode.internal.cache.RegionEntryFactory;
import org.apache.geode.internal.cache.Token;
import org.apache.geode.test.junit.categories.UnitTest;

@Category(UnitTest.class)
public class RegionMapPutTest {
  private final InternalRegion internalRegion = mock(InternalRegion.class);
  private final FocusedRegionMap focusedRegionMap = mock(FocusedRegionMap.class);
  private final CacheModificationLock cacheModificationLock = mock(CacheModificationLock.class);
  private final EntryEventSerialization entryEventSerialization =
      mock(EntryEventSerialization.class);
  private final RegionEntry regionEntry = mock(RegionEntry.class);
  private final EntryEventImpl event = mock(EntryEventImpl.class);
  private boolean ifNew = false;
  private boolean ifOld = false;
  private boolean requireOldValue = false;
  private Object expectedOldValue = null;
  private boolean overwriteDestroyed = false;

  @Before
  public void setup() {
    RegionEntryFactory regionEntryFactory = mock(RegionEntryFactory.class);
    when(regionEntryFactory.createEntry(any(), any(), any())).thenReturn(regionEntry);
    when(internalRegion.getScope()).thenReturn(Scope.LOCAL);
    when(internalRegion.isInitialized()).thenReturn(true);
    when(internalRegion.getCachePerfStats()).thenReturn(mock(CachePerfStats.class));
    when(focusedRegionMap.getEntryMap()).thenReturn(mock(Map.class));
    when(focusedRegionMap.getEntryFactory()).thenReturn(regionEntryFactory);
    when(event.getOperation()).thenReturn(Operation.UPDATE);
    when(event.getKey()).thenReturn("key");
    when(event.getRegion()).thenReturn(internalRegion);
    when(regionEntry.getValueAsToken()).thenReturn(Token.REMOVED_PHASE1);
    when(regionEntry.isRemoved()).thenReturn(true);
  }

  private RegionEntry doPut() {
    RegionMapPut regionMapPut = new RegionMapPut(focusedRegionMap, internalRegion,
        cacheModificationLock, entryEventSerialization, event, ifNew, ifOld, overwriteDestroyed,
        requireOldValue, expectedOldValue);
    return regionMapPut.put();
  }

  @Test
  public void createOnEmptyMapAddsEntry() throws Exception {
    ifNew = true;
    when(event.getOperation()).thenReturn(Operation.CREATE);

    RegionEntry result = doPut();

    assertThat(result).isSameAs(regionEntry);
    verify(event, times(1)).putNewEntry(internalRegion, regionEntry);
    verify(internalRegion, times(1)).basicPutPart2(eq(event), eq(result), eq(true), anyLong(),
        eq(false));
    verify(internalRegion, times(1)).basicPutPart3(eq(event), eq(result), eq(true), anyLong(),
        eq(true), eq(ifNew), eq(ifOld), eq(expectedOldValue), eq(requireOldValue));
  }

  @Test
  public void putOnEmptyMapAddsEntry() throws Exception {
    ifNew = false;
    ifOld = false;
    when(event.getOperation()).thenReturn(Operation.CREATE);

    RegionEntry result = doPut();

    assertThat(result).isSameAs(regionEntry);
    verify(event, times(1)).putNewEntry(internalRegion, regionEntry);
    verify(internalRegion, times(1)).basicPutPart2(eq(event), eq(result), eq(true), anyLong(),
        eq(false));
    verify(internalRegion, times(1)).basicPutPart3(eq(event), eq(result), eq(true), anyLong(),
        eq(true), eq(ifNew), eq(ifOld), eq(expectedOldValue), eq(requireOldValue));
  }

  @Test
  public void updateOnEmptyMapReturnsNull() throws Exception {
    ifOld = true;

    RegionEntry result = doPut();

    assertThat(result).isNull();
    verify(event, never()).putNewEntry(any(), any());
    verify(event, never()).putExistingEntry(any(), any(), anyBoolean(), any());
    verify(internalRegion, never()).basicPutPart2(any(), any(), anyBoolean(), anyLong(),
        anyBoolean());
    verify(internalRegion, never()).basicPutPart3(any(), any(), anyBoolean(), anyLong(),
        anyBoolean(), anyBoolean(), anyBoolean(), any(), anyBoolean());
  }

  @Test
  public void createOnExistingEntryReturnsNull() throws RegionClearedException {
    ifNew = true;
    when(focusedRegionMap.getEntry(event)).thenReturn(mock(RegionEntry.class));
    when(event.getOperation()).thenReturn(Operation.CREATE);

    RegionEntry result = doPut();

    assertThat(result).isNull();
    verify(event, never()).putNewEntry(any(), any());
    verify(event, never()).putExistingEntry(any(), any(), anyBoolean(), any());
    verify(internalRegion, never()).basicPutPart2(any(), any(), anyBoolean(), anyLong(),
        anyBoolean());
    verify(internalRegion, never()).basicPutPart3(any(), any(), anyBoolean(), anyLong(),
        anyBoolean(), anyBoolean(), anyBoolean(), any(), anyBoolean());
  }

  @Test
  public void createOnEntryReturnedFromPutIfAbsentDoesNothing() throws RegionClearedException {
    ifNew = true;
    when(focusedRegionMap.getEntry(event)).thenReturn(mock(RegionEntry.class));
    when(focusedRegionMap.putEntryIfAbsent(event.getKey(), regionEntry))
        .thenReturn(mock(RegionEntry.class));
    when(event.getOperation()).thenReturn(Operation.CREATE);

    RegionEntry result = doPut();

    assertThat(result).isNull();
    verify(event, never()).putNewEntry(any(), any());
    verify(event, never()).putExistingEntry(any(), any(), anyBoolean(), any());
    verify(internalRegion, never()).basicPutPart2(any(), any(), anyBoolean(), anyLong(),
        anyBoolean());
    verify(internalRegion, never()).basicPutPart3(any(), any(), anyBoolean(), anyLong(),
        anyBoolean(), anyBoolean(), anyBoolean(), any(), anyBoolean());
  }

  @Test
  public void createOnExistingEntryWithRemovePhase2DoesCreate() throws RegionClearedException {
    ifNew = true;
    RegionEntry existingRegionEntry = mock(RegionEntry.class);
    when(existingRegionEntry.isRemovedPhase2()).thenReturn(true);
    when(focusedRegionMap.putEntryIfAbsent(event.getKey(), regionEntry))
        .thenReturn(existingRegionEntry).thenReturn(null);
    when(event.getOperation()).thenReturn(Operation.CREATE);

    RegionEntry result = doPut();

    assertThat(result).isSameAs(regionEntry);
    verify(event, times(1)).putNewEntry(internalRegion, regionEntry);
    verify(internalRegion, times(1)).basicPutPart2(eq(event), eq(result), eq(true), anyLong(),
        eq(false));
    verify(internalRegion, times(1)).basicPutPart3(eq(event), eq(result), eq(true), anyLong(),
        eq(true), eq(ifNew), eq(ifOld), eq(expectedOldValue), eq(requireOldValue));
  }

  @Test
  public void updateOnExistingEntryDoesUpdate() throws Exception {
    ifOld = true;
    Object updateValue = "update";
    RegionEntry existingRegionEntry = mock(RegionEntry.class);
    when(focusedRegionMap.getEntry(event)).thenReturn(existingRegionEntry);
    when(event.getNewValue()).thenReturn(updateValue);

    RegionEntry result = doPut();

    assertThat(result).isSameAs(existingRegionEntry);
    verify(event, times(1)).putExistingEntry(eq(internalRegion), eq(existingRegionEntry),
        anyBoolean(), any());
    verify(internalRegion, times(1)).basicPutPart2(eq(event), eq(result), eq(true), anyLong(),
        eq(false));
    verify(internalRegion, times(1)).basicPutPart3(eq(event), eq(result), eq(true), anyLong(),
        eq(true), eq(ifNew), eq(ifOld), eq(expectedOldValue), eq(requireOldValue));
  }

  @Test
  public void putOnExistingEntryDoesUpdate() throws Exception {
    ifOld = false;
    ifNew = false;
    Object updateValue = "update";
    RegionEntry existingRegionEntry = mock(RegionEntry.class);
    when(focusedRegionMap.getEntry(event)).thenReturn(existingRegionEntry);
    when(event.getNewValue()).thenReturn(updateValue);

    RegionEntry result = doPut();

    assertThat(result).isSameAs(existingRegionEntry);
    verify(event, times(1)).putExistingEntry(eq(internalRegion), eq(existingRegionEntry),
        anyBoolean(), any());
    verify(internalRegion, times(1)).basicPutPart2(eq(event), eq(result), eq(true), anyLong(),
        eq(false));
    verify(internalRegion, times(1)).basicPutPart3(eq(event), eq(result), eq(true), anyLong(),
        eq(true), eq(ifNew), eq(ifOld), eq(expectedOldValue), eq(requireOldValue));
  }

}
