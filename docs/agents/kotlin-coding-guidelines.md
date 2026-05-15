# Kotlin Coding Guidelines

Project-specific Kotlin conventions that are easy to miss from code alone. Prefer nearby code style for ordinary Kotlin
formatting and naming.

- Prefer one top-level class, object, interface, or standalone data class per Kotlin file.
- Keep related sealed request types together in one file and related sealed response types together in one file.
- Use a camel-case name for a Kotlin file that does not contain a single top-level class/object/interface/data class.
  Example: `daemonProtocol.kt` instead of `DaemonProtocol.kt`
- Keep class names descriptive. If the purpose of a class has changed and the class name does not adequately describe it
  anymore, rename the class.
- In tests, when expected values cover a data object's properties, compare the complete data object instead of asserting
  every property separately. This keeps the assertion aligned with the object's equality contract and catches newly added
  properties.
- Wildcard imports are acceptable.
