require 'spec_helper'

java_import "clojure.lang.PersistentHashSet"
java_import "clojure.lang.Keyword"

describe Datomizer::Translation do

  let(:ruby_set) { Set.new([:a, 1, Set.new([:b, 2])]) }
  let(:clojure_set) {
    PersistentHashSet.create([Keyword.intern('a'),
                              1,
                              PersistentHashSet.create([Keyword.intern('b'),
                                                        2
                                                       ])
                             ])
  }

  let(:datomic_entity) { Java::DatomicQuery::EntityMap.new(1, 2, 3, 4) }
  let(:datomizer_entity) { Datomizer::Entity.new(datomic_entity) }


  describe "#from_clj" do
    context "supplied an IPersistentSet" do
      subject { Datomizer::Translation.from_clj(clojure_set) }

      it 'returns a ruby Set with translated members' do
        expect(subject).to eq(ruby_set)
      end
    end

    context "supplied a Datomic entity" do
      subject { Datomizer::Translation.from_clj(datomic_entity) }

      it 'wraps it in a Datomizer entity' do
        expect(subject).to eq(datomizer_entity)
      end
    end
  end

  describe "#from_clj" do
    context "supplied a ruby Set" do
      subject { Datomizer::Translation.from_ruby(ruby_set) }

      it 'returns a PersistentHashSet with translated members' do
        expect(subject).to clojure_equal(clojure_set)
      end
    end

    context "supplied a Datomizer entity" do

      subject { Datomizer::Translation.from_ruby(datomizer_entity) }

      it 'returns the wrapped Datomic entity' do
        expect(subject).to equal(datomic_entity)
      end
    end
  end
end
