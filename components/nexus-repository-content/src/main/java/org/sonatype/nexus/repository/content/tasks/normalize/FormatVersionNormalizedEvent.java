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
package org.sonatype.nexus.repository.content.tasks.normalize;

import org.sonatype.nexus.common.event.EventWithSource;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Nexus Event to indicate if the NormalizeComponentVersionTask has finished for the specified format
 */
public class FormatVersionNormalizedEvent
    extends EventWithSource
{
  private String format;

  public FormatVersionNormalizedEvent() {
    // deserialization
  }

  public FormatVersionNormalizedEvent(final String format) {
    this.format = checkNotNull(format);
  }

  public String getFormat() {
    return format;
  }
}
