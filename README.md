# Dalton

## Datomic for JRuby

**NB: This project is very alpha.  Expect things to change rapidly and without warning.**

[![John Dalton][john-dalton-img]][john-dalton-wiki]

### The basics

``` ruby
connection = Dalton::Connection.connect("datomic:mem://my-cool-db")

# transactions!
connection.transact([:'db/add', Dalton::Utility.tempid('db.part/db'), :'db/ident', :'hello/world'])
connection.retract(entity)

# latest db
db = connection.refresh

# querying
connection.db.query([:find, '?e', :where, ['?e', :'db/ident', :'hello/world']])
```

### TODO

* Remove the dependency on Zweikopf and restructure data handling over the bridge
* More and better docs, especially for `Dalton::Model`
* Test-drive `Dalton::Model`'s design and fill in missing features
* Remove the internal `@db` reference from `Dalton::Connection`
* Improve naming of `Dalton::Utility` functions and consider extending / separating
* Non-Jruby support, potentially through transit and a local peer server

[john-dalton-img]: https://upload.wikimedia.org/wikipedia/commons/thumb/d/d4/John_Dalton_by_Charles_Turner.jpg/240px-John_Dalton_by_Charles_Turner.jpg
[john-dalton-wiki]: https://en.wikipedia.org/wiki/John_Dalton
