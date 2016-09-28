package mesosphere.mesos.matcher

import mesosphere.marathon.core.task.Task
import org.apache.mesos.{ Protos => Mesos }

import scala.collection.JavaConverters._

object PersistentVolumeMatcher {
  def matchVolumes(
    offer: Mesos.Offer,
    waitingTasks: Iterable[Task.Reserved]): Option[VolumeMatch] = {

    // find all offered persistent volumes
    val availableVolumes: Map[String, Mesos.Resource] = offer.getResourcesList.asScala.collect {
      case resource: Mesos.Resource if resource.hasDisk && resource.getDisk.hasPersistence =>
        resource.getDisk.getPersistence.getId -> resource
    }.toMap

    def resourcesForTask(volumeIds: Iterable[String]): Option[Iterable[Mesos.Resource]] = {
      if (volumeIds.forall(availableVolumes.contains))
        Some(volumeIds.flatMap(id => availableVolumes.get(id)))
      else
        None
    }

    waitingTasks.toStream.
      flatMap { task =>
        resourcesForTask(task.reservation.volumeIds.map(_.idString)).
          flatMap(rs => Some(VolumeMatch(task, rs)))
      }.
      headOption
  }

  case class VolumeMatch(task: Task.Reserved, persistentVolumeResources: Iterable[Mesos.Resource])
}