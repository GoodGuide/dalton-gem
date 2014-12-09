module Dalton
  module Model
    module HasDatabase
      attr_accessor :db

      def datomic_uri
        raise "please define datomic_uri on #{self.class.name}"
      end

      def datomic_connection
        Dalton::Connection.connect(datomic_uri)
      end

      def refresh_datomic!
        @db = datomic_connection.db
      end

      def find(model, *args)
        model.finder(@db, *args)
      end
    end
  end
end
