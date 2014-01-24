require 'spec_helper'

java_import "clojure.lang.PersistentHashSet"
java_import "java.util.HashSet"
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

  let(:ruby_keyword) { :xorn }
  let(:clojure_keyword) { Keyword.intern('xorn') }

  let(:ruby_keyword_datalog_variable) { :'?e' }
  let(:clojure_symbol_datalog_variable) { Java::ClojureLang::Symbol.intern('?e') }

  let(:ruby_keyword_datalog_source) { :'$' }
  let(:clojure_symbol_datalog_source) { Java::ClojureLang::Symbol.intern('$') }

  describe "#from_clj" do
    context "with a (clojure) IPersistentSet" do
      subject { Datomizer::Translation.from_clj(clojure_set) }

      it 'returns a ruby Set with translated members' do
        expect(subject).to eq(ruby_set)
      end
    end

    context "with a (clojure) Datomic entity" do
      subject { Datomizer::Translation.from_clj(datomic_entity) }

      it 'wraps it in a Datomizer entity' do
        expect(subject).to eq(datomizer_entity)
      end
    end

    context "with a clojure Keyword" do
      subject { Datomizer::Translation.from_clj(clojure_keyword) }

      it 'returns the equivalent ruby keyword' do
        expect(subject).to eq(ruby_keyword)
      end
    end

    context "with a clojure Symbol" do
      subject { Datomizer::Translation.from_clj(clojure_symbol_datalog_variable) }

      it 'returns the equivalent ruby keyword' do
        expect(subject).to eq(ruby_keyword_datalog_variable)
      end
    end
  end

  describe "#from_ruby" do
    context "with a ruby Set" do
      subject { Datomizer::Translation.from_ruby(ruby_set) }

      it 'returns a PersistentHashSet with translated members' do
        expect(subject).to clojure_equal(clojure_set)
      end
    end

    context "with a (ruby) Datomizer entity" do
      subject { Datomizer::Translation.from_ruby(datomizer_entity) }

      it 'returns the wrapped Datomic entity' do
        expect(subject).to equal(datomic_entity)
      end
    end

    context "with a ruby Keyword starting with '?' (e.g. for use as a datalog variable)" do
      subject { Datomizer::Translation.from_ruby(ruby_keyword_datalog_variable) }

      it 'returns the equivalent clojure symbol' do
        expect(subject).to clojure_equal(clojure_symbol_datalog_variable)
      end
    end

    context "with a ruby Keyword starting with '$' (e.g. for use as a datalog source)" do
      subject { Datomizer::Translation.from_ruby(ruby_keyword_datalog_source) }

      it 'returns the equivalent clojure symbol' do
        expect(subject).to clojure_equal(clojure_symbol_datalog_source)
      end
    end
  end
end

