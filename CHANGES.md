## 0.5.2

## Enhancements
* OM-127: om.core/refresh!
* OM-28: om.core/update-state!
* protocols for local state
* Pure implementation methods now provided in immutable map

## 0.5.1

## Changes
* Requires ClojureScript 0.0-2173
* IOmSwap protocol for app state representations besides standard atom
* :instrument option for om.core/root
* Add IDisplayName to support React Chrome Dev Tools
* om.core/build takes :ctor option to provide React backing component
  class besides om.core/Pure

## Fixes
* OM-122: IWillReceiveProps protocol

## 0.5.0

## Changes
* Upgrade to React 0.9.0

## 0.5.0-rc1

### Breaking Changes
* IDidMount, IDidUpdate no longer take node to match React 0.9.0-rc1

### Enhancements
* om.dom/render-to-str added
* OM-72: :path option for om.core/root

## 0.4.2

### Enhancements
* OM-112: transact! special cases when korks is nil

### Bug fixes
* OM-114: om.core/update! doesn't pass tag

## 0.4.1

### Bug fixes
* OM-110: transact! broken for empty path

## 0.4.0

### Breaking Changes
* `om.core/root` signature changed to be more like `om.core/build`
* `om.core/update!` no longer takes keys, or function, or function args
* `om.core/transact!` no longer takes arguments after function

### Enhancements
* `om.core/transact!` now takes optional 4th argument, a tag
* `om.core/update!` now takes optional 4th argument, a tag
* `om.core/root` now supports `:tx-listen` option to observe all transactions

### Bug fixes
* `om.core/shared` was brittle, now works without cursors
