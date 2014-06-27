require 'spec_helper'

describe Dalton::Database do
  include DatomicContext

  describe 'data storage, query, and retrieval' do

    let(:attribute) { :'db/doc' }
    let(:value) { 'This is a test entity.' }

    let!(:transaction_result) { conn.transact([{:'db/id' => Dalton::Connection.tempid, attribute => value}]) }

    let(:entity_id) { transaction_result.tempids.values.first }

    let(:query) { [:find, :'?e', :where, [:'?e', attribute, value]] }
    let(:edn_query) { '[:find ?e :where [?e :db/doc "This is a test entity."]]' }

    describe '#q(query)' do

      shared_examples_for 'a query' do
        it 'runs the query' do
          expect(query_result).to be_a(Set)
          expect(query_result.size).to eq(1)

          entity_id = query_result.first.first
          entity = db.entity(entity_id)

          expect(entity[attribute]).to eq(value)
        end
      end

      context "when the query is a ruby data structure" do
        let(:query_result) { db.q(query) }

        it_behaves_like 'a query'
      end

      context "when the query is an EDN string" do
        let(:query_result) { db.q(edn_query) }

        it_behaves_like 'a query'
      end
    end

    describe '#entity(entity_id)' do
      let(:entity) { db.entity(entity_id) }

      it 'fetches an entity from the database' do
        expect(entity[attribute]).to eq(value)
      end
    end

    describe '#retrieve(query)' do
      let(:results) { db.retrieve(query) }
      let(:entity) { results.first }

      it 'runs a query and retrieves entities' do
        expect(results.to_a.size).to eq(1)
        expect(entity[attribute]).to eq(value)
      end

      it 'returns a lazy enumerator' do
        expect(results).to be_a(Enumerator::Lazy)
      end
    end

  end

  describe "#attribute" do
    let(:attribute) { db.attribute(:'db/doc') }
    it "retrieves an attribute definition" do
      expect(attribute.hasAVET).to eq(false)
      expect(attribute.hasFulltext).to eq(true)
      expect(attribute.hasNoHistory).to eq(false)
      expect(attribute.id).to be_a(Fixnum)
      expect(attribute.ident).to eq(:'db/doc')
      expect(attribute.isComponent).to eq(false)
      expect(attribute.isIndexed).to eq(false)
      expect(attribute.unique).to eq(nil)
      expect(attribute.valueType).to eq(:'db.type/string')
    end
  end
end


