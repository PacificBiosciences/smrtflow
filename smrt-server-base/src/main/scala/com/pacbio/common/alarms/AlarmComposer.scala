package com.pacbio.common.alarms

import com.pacbio.common.dependency.Singleton

import scala.collection.mutable.ArrayBuffer

trait AlarmComposer {
  val _alarms = ArrayBuffer.empty[Singleton[AlarmRunner]]

  def addAlarm(alarm: Singleton[AlarmRunner]) = _alarms += alarm

  def alarms(): Seq[AlarmRunner] = _alarms.map(_()).toSeq
}
