module Dalton
  module Model
    module ClassMethods
      def schema(name=nil, opts={}, &b)
        return @schema unless block_given?

        if name.is_a? Hash
          opts = name
          name = nil
        end

        @datomic_name = name
        @datomic_name ||= self.name
          .gsub(/[^[:alpha:]]+/, '-')
          .gsub(/(?<=[[:lower:]])(?=[[:upper:]])/, '-')
          .downcase

        @namespace = opts.fetch(:namespace) { Model.namespace } \
          or raise ArgumentError.new("no namespace configured for #{self} or globally")
        @partition = opts.fetch(:partition) { Model.partition } \
          or raise ArgumentError.new("no partition configured for #{self} or globally")
        @partition = :"db.part/#{partition}" unless partition.to_s.start_with?('db.part/')

        Model.registry[datomic_type.to_s] = self

        @schema = Schema.new(self, &b)
      end

      def install_schema!
        raise ArgumentError.new("no schema defined for #{self}!") unless schema
        schema.install!
      end

      def install_base!
        transact <<-EDN
          [{:db/id #db/id[:db.part/db]
            :db/ident :#{partition}
            :db.install/_partition :db.part/db}

           {:db/id #db/id[:db.part/db]
            :db/ident :#{namespace}/type
            :db/valueType :db.type/ref
            :db/cardinality :db.cardinality/one
            :db/doc "A model's type"
            :db.install/_attribute :db.part/db}]
        EDN
      end
    end

    class Schema
      include Dalton::Utility

      attr_reader :model, :transactions
      def initialize(model, &block)
        @model = model
        @transactions = []
        declare_type
        instance_exec(&block)
      end

      def name
        model.datomic_name
      end

      def partition
        model.partition
      end

      def namespace
        model.namespace
      end

      def key(key, subkey=nil)
        if subkey
          :"#{namespace}.#{key}/#{subkey}"
        else
          :"#{namespace}/#{key}"
        end
      end

      def declare_type
        edn [:'db/add', Peer.tempid(kw(partition)), :'db/ident', key(:type, name)]
      end

      def edn(edn)
        @transactions << edn
      end

      def attribute(attr_key, opts={})
        edn(
          :'db/id' => opts.fetch(:id) { Peer.tempid(kw('db.part/db')) },
          :'db/ident' => kw(opts.fetch(:ident) { key(model.datomic_name, attr_key) }),
          :'db/valueType' => :"db.type/#{opts.fetch(:value_type)}",
          :'db/cardinality' => :"db.cardinality/#{opts.fetch(:cardinality, :one)}",
          :'db/doc' => opts.fetch(:doc) { "The #{attr_key} attribute" },
          :'db.install/_attribute' => :'db.part/db',
        )
      end

      def install!
        transactions.each do |t|
          model.transact([t])
        end
      end
    end
  end
end
