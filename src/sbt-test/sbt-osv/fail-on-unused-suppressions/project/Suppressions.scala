import net.nmoncho.sbt.osv.SuppressionRule
import net.nmoncho.sbt.osv.settings.SuppressionSettings

// Suppression scenarios, referenced from `test` through `set`
object Suppressions {

  private val used   = SuppressionRule("GHSA-p66x-2cv9-qq3v", "matches a found vulnerability", "build")
  private val unused = SuppressionRule("GHSA-xxxx-xxxx-xxxx", "matches nothing", "build")

  val onlyUsed: SuppressionSettings      = SuppressionSettings(suppressions = Set(used))
  val usedAndUnused: SuppressionSettings = SuppressionSettings(suppressions = Set(used, unused))
}
