package com.pacbio.secondary.smrtlink

import com.pacbio.secondary.smrtlink.analysis.tools.timeUtils
import com.pacbio.secondary.smrtlink.services.BaseSmrtService
import com.typesafe.scalalogging.LazyLogging

/**
 * Created by mkocher on 6/24/15.
 *
 */
trait SecondaryAnalysisBaseMicroService extends BaseSmrtService
with timeUtils // this should be removed
with LazyLogging
