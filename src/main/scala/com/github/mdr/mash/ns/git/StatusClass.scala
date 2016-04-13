package com.github.mdr.mash.ns.git

import com.github.mdr.mash.evaluator.MashClass
import com.github.mdr.mash.ns.os.PermissionsSectionClass
import com.github.mdr.mash.inference.Type
import com.github.mdr.mash.ns.core.StringClass
import com.github.mdr.mash.evaluator.Field
import com.github.mdr.mash.ns.time.DateTimeClass
import com.github.mdr.mash.evaluator.MashString
import com.github.mdr.mash.functions.MashMethod
import com.github.mdr.mash.ns.core.ObjectClass
import com.github.mdr.mash.evaluator.Arguments
import com.github.mdr.mash.evaluator.MashObject
import com.github.mdr.mash.inference.ConstantMethodTypeInferenceStrategy
import com.github.mdr.mash.ns.os.PathClass
import com.github.mdr.mash.evaluator.MashList
import com.github.mdr.mash.ns.core.NumberClass
import com.github.mdr.mash.evaluator.MashNumber

object StatusClass extends MashClass("git.Status") {

  object Fields {
    val Branch = Field("branch", "Current branch", Type.Instance(StringClass))
    val RemoteTrackingBranch = Field("remoteTrackingBranch", "Name of the remote tracking branch (if any, else null)", Type.Instance(StringClass))
    val AheadCount = Field("aheadCount", "Number of commits that the local branch is ahead of the remote-tracking branch", Type.Instance(NumberClass))
    val BehindCount = Field("behindCount", "Number of commits that the local branch is behind the remote-tracking branch", Type.Instance(NumberClass))
    val Added = Field("added", "New files that have been staged", Type.Seq(Type.Tagged(StringClass, PathClass)))
    val Changed = Field("changed", "Changed files that have been staged", Type.Seq(Type.Tagged(StringClass, PathClass)))
    val Missing = Field("missing", "Files that have been deleted, but not staged", Type.Seq(Type.Tagged(StringClass, PathClass)))
    val Modified = Field("modified", "Modified files that have not been staged", Type.Seq(Type.Tagged(StringClass, PathClass)))
    val Removed = Field("removed", "Files that have been deleted and staged", Type.Seq(Type.Tagged(StringClass, PathClass)))
    val Untracked = Field("untracked", "Untracked files", Type.Seq(Type.Tagged(StringClass, PathClass)))
  }

  import Fields._

  override lazy val fields = Seq(Branch, RemoteTrackingBranch, AheadCount, BehindCount, Added, Changed, Missing, Modified, Removed, Untracked)

  def summary = "Show the status of the git repository"

  case class Wrapper(obj: MashObject) {
    private def unmashify(field: Field): Seq[String] =
      obj(field).asInstanceOf[MashList].items.map(_.asInstanceOf[MashString].s)
    def added = unmashify(Added)
    def changed = unmashify(Changed)
    def missing = unmashify(Missing)
    def modified = unmashify(Modified)
    def removed = unmashify(Removed)
    def untracked = unmashify(Untracked)
    def hasChangesToBeCommitted = added.nonEmpty || changed.nonEmpty || removed.nonEmpty
    def hasUnstagedChanges = modified.nonEmpty || missing.nonEmpty
    def branch = obj(Branch).asInstanceOf[MashString].s
    def remoteTrackingBranchOpt: Option[String] = Option(obj(RemoteTrackingBranch)).map(_.asInstanceOf[MashString].s)
    def aheadCount = obj(AheadCount).asInstanceOf[MashNumber].asInt.get
    def behindCount = obj(BehindCount).asInstanceOf[MashNumber].asInt.get
  }

}