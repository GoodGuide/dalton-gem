require 'spec_helper'

java_import "clojure.lang.Keyword"
java_import "clojure.lang.PersistentVector"
java_import "clojure.lang.IPersistentMap"

describe Dalton::Utility do
  include Dalton::Utility

  describe '#read_edn' do
    it 'reads EDN' do
      read_data = read_edn('[1 2 3]')
      expected_data = PersistentVector.create([1, 2, 3])
      expect(read_data).to eq(expected_data)
    end

    it 'understands tempid literal tags (#db/id)' do
      tempid = read_edn('#db/id[:db.part/db]')
      expect(tempid).to be_a(IPersistentMap)
      expect(tempid[Keyword.intern('part')]).to eq(Keyword.intern('db.part/db'))
      expect(tempid[Keyword.intern('idx')]).to be_a(Numeric)
      expect(tempid[Keyword.intern('idx')]).to be < 0
    end

    it 'understands database function literal tags (#db/fn)' do
      database_function = read_edn('#db/fn{:lang "clojure" :params [db foo] :code "(reverse foo)"}')
      expect(database_function).to be_a(IPersistentMap)
      # with complicated structure we probably don't need to test...
    end
  end

  describe "#tag" do
    it "tag a symbol with metadata" do
      expect(meta(tag(sym('foo'), sym('String'))).to_edn).to eq('{:tag String}')
    end
  end
end
