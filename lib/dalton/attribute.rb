module Dalton
  class Attribute
    def initialize(datomic_attribute)
      @datomic_attribute = datomic_attribute
    end

    def method_missing(name, *args, &block)
      if @datomic_attribute.respond_to?(name)
        Translation.from_clj(@datomic_attribute.send(name, *args, &block))
      else
        super
      end
    end

  end
end
