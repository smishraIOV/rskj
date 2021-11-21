# Notes on sr-rentManager

Compare with a commit about 3 down, the latest 3 are related to this rent.

```@bash
git diff --stat 7fdc750e77339f048b1a4691cf09de4a169fa177
 rskj-core/src/main/java/co/rsk/core/TransactionExecutorFactory.java |   2 +-
    - add todo note to rename newInstance() to newLocalInstance
 
 rskj-core/src/main/java/co/rsk/db/MutableTrieCache.java             |   6 ++++
    - add initRentManager();
 
 rskj-core/src/main/java/co/rsk/db/MutableTrieImpl.java              |   6 ++++
    - same as above

 rskj-core/src/main/java/co/rsk/storagerent/RentManager.java         |  89 +++++++++++++++++++++++++++++++++++++++++++++++++
    - MAJOR change: a new package to track nodes for rent updates

 rskj-core/src/main/java/co/rsk/trie/MutableTrie.java                 |   3 ++
    - add initRentManager()
 
 rskj-core/src/main/java/co/rsk/trie/Trie.java                       |  46 +++++++++++++++++++++----
 
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
 rskj-core/src/test/java/co/rsk/storagerent/RentManagerDSLTest.java  | 153 ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
 rskj-core/src/test/java/co/rsk/storagerent/RentManagerTest.java     | 126 +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
 rskj-core/src/test/java/co/rsk/test/World.java                      |   4 +++
 rskj-core/src/test/resources/dsl/storagerent/rentManager.txt        |   9 +++++
 15 files changed, 479 insertions(+), 8 deletions(-) 
 ```
