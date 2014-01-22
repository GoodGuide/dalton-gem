module Datomizer
  module Marshalling

    autoload :Datomization, File.join(File.dirname(__FILE__), 'marshalling', 'datomization')
    autoload :Serialization, File.join(File.dirname(__FILE__), 'marshalling', 'serialization')

    module_function

    def ref_type(entity, key)
      field = entity.db.entity(Keyword.intern(key.to_s))
      Translation.from_clj(field.get(Keyword.intern('ref/type')))
    end
  end
end
