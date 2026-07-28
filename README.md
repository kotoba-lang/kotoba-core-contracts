# kotoba-core-contracts

CLJC/EDN authority for Kotoba source classification and runtime boundary data.

This repo owns semantic contracts used by the launcher/runtime:

- `resources/kotoba/lang/source_contract.edn`
- `resources/kotoba/runtime/capability_contract.edn`
- `src/kotoba/core/contracts.cljc`
- `src/kotoba/core/actor_capability.cljc` — actor responsibility to bounded
  `actor:host` authority, envelope emission, and host-neutral revalidation

Launchers and native/Wasm adapters consume these contracts. They do not own
source kind semantics, capability ids, or host import ABI shape.

## Test

```sh
clojure -M:test
```
