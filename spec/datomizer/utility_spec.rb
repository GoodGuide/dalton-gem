require 'rspec'
require_relative('../../lib/datomizer')

describe Datomizer::Utility do
  describe '#read_edn' do
    it 'reads EDN' do
      read_data = Datomizer::Utility.read_edn('[1 2 3]')
      expected_data = Java::ClojureLang::PersistentVector.create([1, 2, 3])
      expect(read_data).to eql(expected_data)
    end

    it 'understands tempid literal tags (#db/id)' do
      tempid = Datomizer::Utility.read_edn('#db/id[:db.part/db]')
      expect(tempid).to be_a(IPersistentMap)
      expect(tempid[Java::ClojureLang::Keyword.intern('part')]).to eql(Java::ClojureLang::Keyword.intern('db.part/db'))
      expect(tempid[Java::ClojureLang::Keyword.intern('idx')]).to be_a(Numeric)
      expect(tempid[Java::ClojureLang::Keyword.intern('idx')]).to be < 0
    end

    it 'understands database function literal tags (#db/fn)' do
      database_function = Datomizer::Utility.read_edn('#db/fn{:lang "clojure" :params [db foo] :code "(reverse foo)"}')
      expect(database_function).to be_a(IPersistentMap)
      # with complicated structure we probably don't need to test...
    end
  end
end
