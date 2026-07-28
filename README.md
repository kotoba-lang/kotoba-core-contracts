# kotoba-core-contracts

CLJC/EDN authority for Kotoba source classification, package contracts, and
runtime boundary data.

This repo owns semantic contracts used by the launcher/runtime:

- `resources/kotoba/lang/source_contract.edn`
- `resources/kotoba/runtime/capability_contract.edn`
- `src/kotoba/core/contracts.cljc`
- `src/kotoba/core/actor_capability.cljc` — actor responsibility to bounded
  `actor:host` authority, envelope emission, and host-neutral revalidation
- `src/kotoba/lang/package_contract.cljc`
- `src/kotoba/lang/package_registry.cljc`
- `src/kotoba/lang/package_registry_network.clj`
- `lang/package-conformance/`

Launchers and native/Wasm adapters consume these contracts. They do not own
source kind semantics, package validation/registry semantics, capability ids,
or host import ABI shape.

## Test

```sh
clojure -M:test
```
