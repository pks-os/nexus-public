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
package org.sonatype.nexus.bootstrap.osgi;

import java.nio.file.Path;
import java.util.Properties;

public class ProNexusEdition extends NexusEdition
{

  @Override
  public NexusEditionType getEdition() {
    return NexusEditionType.PRO;
  }

  @Override
  public NexusEditionFeature getEditionFeature() {
    return NexusEditionFeature.PRO_FEATURE;
  }

  @Override
  public void adjustEditionProperties(final Path workDirPath, final Properties properties) {
    if (shouldSwitchToProStarter(workDirPath)) {
      adjustEditionPropertiesToStarter(properties);
      createEditionMarker(workDirPath, NexusEditionType.PRO_STARTER);
      return;
    }
    if (shouldSwitchToOss(workDirPath)) {
      adjustEditionPropertiesToOSS(properties);
      return;
    }
    createEditionMarker(workDirPath, getEdition());
  }
}
