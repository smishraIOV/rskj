# Notes on sr-rentManager

Compare with a commit where rent-related changes start.

```@bash
git diff --stat 7fdc750e77339f048b1a4691cf09de4a169fa177
rskj-core/src/main/java/co/rsk/core/TransactionExecutorFactory.java |   2 +-
   - add todo note to rename newInstance() to newLocalInstance

rskj-core/src/main/java/co/rsk/db/MutableTrieCache.java             |   6 ++++
   - add initRentManager();

rskj-core/src/main/java/co/rsk/db/MutableTrieImpl.java              |   6 ++++
   - same as above

```

continuing, but isolated for focus

```
rskj-core/src/main/java/co/rsk/storagerent/RentManager.java         |  73 +++++++++++++++++++++++++++++++++++++++++++++++++
   - ADD new class (and in new `storagerent` package) to track nodes for rent updates
   - given a `keySlice` and `trie` the main method `trackNodes` returns
      - trackedNodes: all nodes found from keyslice
      - mismatches: keyslices not associated with any nodes in the given trie
   - the idea is that Repository will interact with this class
      - and use rent computation logic (in separate class)
   - but the initial instantiation will happen inside `TransactionExecutor`
```

```
rskj-core/src/main/java/co/rsk/trie/MutableTrie.java                 |   3 ++
    - add initRentManager()
rskj-core/src/main/java/co/rsk/trie/Trie.java                       |  49 +++++++++++++++++++++----
```
Trie changes (using `ks` for trieKeySlice)
- define `trackNodes(ks, trie)` which just points to `rentManager.trackNodes(ks, trie)`
- add `trackNodes(ks, this)` to `put` and `find` (i.e. `get`).
   - since `delete` is just a `put(k, null)`, those get tracked too 
   - rename `find(ks)` to `findInTrie(ks)` [return a trie node]
   - `find(key)` remains unchanged
   - also renamed `put(ks)` to `putInTrie(ks)` 
   - recall there's another method `findNodes` which returns a **list** of trie-nodes (recursively returning the path). That's what trackNodes uses

FJ says the nodeTracking should ideally happen in Repository, but that would require moving the `put` and `find` methods to mutableTrie.

continuing,

```
rskj-core/src/main/java/co/rsk/trie/TrieKeySlice.java               |   7 ++++
   - add toString() for debugging // remove later

rskj-core/src/main/java/org/ethereum/core/Repository.java           |   3 ++
   - add  initRentManager(RentManager rentManager);

rskj-core/src/main/java/org/ethereum/core/TransactionExecutor.java  |  21 ++++++++++++
   - simple so far -- add new field,  getter and setter for rentManager class

rskj-core/src/main/java/org/ethereum/db/MutableRepository.java      |   6 ++++
   - add initRentManager()

rskj-core/src/main/java/org/ethereum/vm/program/Storage.java        |   6 ++++
   - add initRentManager()

# Tests
rskj-core/src/test/java/co/rsk/storagerent/RentManagerDSLTest.java  | 152 ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
rskj-core/src/test/java/co/rsk/storagerent/RentManagerTest.java     | 127 +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
rskj-core/src/test/java/co/rsk/test/World.java                      |   4 +++
rskj-core/src/test/resources/dsl/storagerent/rentManager.txt        |   9 +++++
 ```


### Misc
I enabled std streams in `build.gradle` for testing

```
testLogging {
        showStandardStreams = true
...
```

`rentManager.trackedNode` is a HashSet of trie nodes. This is converted to list for testing. Index for list entry?