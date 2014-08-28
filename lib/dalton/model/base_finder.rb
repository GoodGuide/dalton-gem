module Dalton
  module Model
    class NotFound < StandardError
      attr_reader :model, :id
      def initialize(model, id)
        @model = model
        @id = id
      end

      def message
        "Could not find #{model} with id #{id}"
      end
    end

    class BaseFinder
      include Enumerable
      include Dalton::Utility

      # should be overridden automatically
      def model
        raise "abstract"
      end

      def inspect
        translated = Translation.from_ruby(all_constraints).to_edn[1..-2]
        "#<#{self.class.name} ##{db.basis_t} :where #{translated}>"
      end

      attr_reader :db, :constraints
      def initialize(db, constraints=[])
        @db = db
        @constraints = constraints
      end

      def where(*constraints)
        new_constraints = @constraints.dup
        constraints.each do |c|
          case c
          when Array
            new_constraints << c
          when Hash
            interpret_constraints(c, &new_constraints.method(:<<))
          end
        end

        self.class.new(@db, new_constraints)
      end

      def entity(id)
        entity = @db.entity(id)

        unless entity.get(model.datomic_type_key) == model.datomic_type
          raise NotFound.new(model, id)
        end

        model.new(entity)
      end

      def results
        query = [:find, sym('?e'), :in, sym('$'), :where, *all_constraints]
        q(query).lazy.map do |el|
          model.new(@db.entity(el.first))
        end
      end

      def type_constraint
        [sym('?e'), model.datomic_type_key, model.datomic_type]
      end

      def all_constraints
        [type_constraint, *constraints]
      end

      def each(&b)
        results.each(&b)
      end

      def with_model(model)
        model.finder(@db)
      end

    private

      def interpret_constraints(hash, &b)
        return enum_for(:interpret_constraints, hash) unless block_given?

        hash.each do |key, value|
          attribute = model.get_attribute(key)
          yield [sym('?e'), attribute.datomic_attribute, attribute.dump(value)]
        end
      end

      def q(query)
        translated_query = Translation.from_ruby(query)
        Model.logger.info("datomic.q #{translated_query.to_edn}")
        result = @db.q(translated_query)
        Translation.from_clj(result)
      end
    end
  end
end
