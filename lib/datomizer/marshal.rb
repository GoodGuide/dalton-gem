module Datomizer
  module Marshal

    # Based on work published by Chas Emerick here: https://gist.github.com/cemerick/3e615a4d42b88ccefdb4

    SCHEMA = <<-EDN
      [[:db/add #db/id[:db.part/user -10] :db/ident :coll/list]
       [:db/add #db/id[:db.part/user -11] :db/ident :coll/map]

       {:db/id #db/id[:db.part/db]
        :db/ident :coll/type
        :db/valueType :db.type/keyword
        :db/cardinality :db.cardinality/one
        :db/isComponent true
        :db/doc "Keyword indicating type of collection represented by the attributes rooted at this entity, either :coll/list or :coll/map."
        :db.install/_attribute :db.part/db}

       {:db/id #db/id[:db.part/db]
        :db/ident :list/str-val
        :db/valueType :db.type/string
        :db/cardinality :db.cardinality/one
        :db/isComponent true
        :db/doc "String cons cell value."
        :db.install/_attribute :db.part/db}

       {:db/id #db/id[:db.part/db]
        :db/ident :list/ref-val
        :db/valueType :db.type/ref
        :db/cardinality :db.cardinality/one
        :db/isComponent true
        :db/doc "Non-scalar cons cell value."
        :db.install/_attribute :db.part/db}

       {:db/id #db/id[:db.part/db]
        :db/ident :list/next
        :db/valueType :db.type/ref
        :db/cardinality :db.cardinality/one
        :db/isComponent true
        :db/doc "Ref to next cons."
        :db.install/_attribute :db.part/db}

       {:db/id #db/id[:db.part/db]
        :db/ident :map/entry
        :db/valueType :db.type/ref
        :db/cardinality :db.cardinality/many
        :db/isComponent true
        :db/doc "Refs to map entries."
        :db.install/_attribute :db.part/db}

       {:db/id #db/id[:db.part/db]
        :db/ident :map/key
        :db/valueType :db.type/keyword
        :db/cardinality :db.cardinality/one
        :db/doc "Key(word) of a map entry"
        :db.install/_attribute :db.part/db}

       {:db/id #db/id[:db.part/db]
        :db/ident :map/str-val
        :db/valueType :db.type/string
        :db/cardinality :db.cardinality/one
        :db/doc "String val of a map entry"
        :db.install/_attribute :db.part/db}

       {:db/id #db/id[:db.part/db]
        :db/ident :map/ref-val
        :db/valueType :db.type/ref
        :db/cardinality :db.cardinality/one
        :db/isComponent true
        :db/doc "Non-scalar map entry value."
        :db.install/_attribute :db.part/db}]
      EDN
  end
end
