package com.pacbio.secondary.smrtlink

import com.pacbio.common.services.BaseSmrtService
import com.pacbio.secondary.analysis.tools.timeUtils
import com.typesafe.scalalogging.LazyLogging

/**
 * Created by mkocher on 6/24/15.
 *
 */
trait SecondaryAnalysisBaseMicroService extends BaseSmrtService
with timeUtils // this should be removed
with LazyLogging
