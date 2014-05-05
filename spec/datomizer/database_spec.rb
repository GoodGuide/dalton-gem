require 'spec_helper'

describe Dalton::Database do

  let(:uri) { 'datomic:mem://spec' }
  let(:d) { Dalton::Database.new(uri) }

  before do
    d.create
    d.connect
    d.refresh
  end

  after do
    d.destroy
  end

  describe 'data storage, query, and retrieval' do

    let(:attribute) { :'db/doc' }
    let(:value) { 'This is a test entity.' }

    let!(:transaction_result) { d.transact([{:'db/id' => Dalton::Database.tempid, attribute => value}]) }

    let(:entity_id) { transaction_result.tempids.values.first }

    let(:query) { [:find, :'?e', :where, [:'?e', attribute, value]] }
    let(:edn_query) { '[:find ?e :where [?e :db/doc "This is a test entity."]]' }

    describe '#transact(datoms)' do

      it 'stores data' do
        expect(d.q(query).size).to eq(1)
      end

      it 'returns a transaction result' do
        expect(transaction_result.db_before).to be_a(Java::Datomic::Database)
        expect(transaction_result.db_after).to be_a(Java::Datomic::Database)
        expect(transaction_result.tx_data).to be_a(Array)
        expect(transaction_result.tempids).to be_a(Hash)
      end

      it 'refreshes the database' do
        expect(d.db).to equal(transaction_result.db_after)
      end
    end

    describe '#q(query)' do

      shared_examples_for 'a query' do
        it 'runs the query' do
          expect(query_result).to be_a(Set)
          expect(query_result.size).to eq(1)

          entity_id = query_result.first.first
          entity = d.entity(entity_id)

          expect(entity[attribute]).to eq(value)
        end
      end

      context "when the query is a ruby data structure" do
        let(:query_result) { d.q(query) }

        it_behaves_like 'a query'
      end

      context "when the query is an EDN string" do
        let(:query_result) { d.q(edn_query) }

        it_behaves_like 'a query'
      end
    end

    describe '#entity(entity_id)' do
      let(:entity) { d.entity(entity_id) }

      it 'fetches an entity from the database' do
        expect(entity[attribute]).to eq(value)
      end
    end

    describe '#retrieve(query)' do
      let(:results) { d.retrieve(query) }
      let(:entity) { results.first }

      it 'runs a query and retrieves entities' do
        expect(results.size).to eq(1)
        expect(entity[attribute]).to eq(value)
      end
    end

    describe '#retract(entity)' do

      shared_examples_for "a retraction" do
        it 'retracts the entity' do
          expect(d.q(query).size).to eq(0)
        end
      end

      context "when supplied an id" do
        before do
          d.retract(entity_id)
        end

        it_behaves_like 'a retraction'
      end

      context "when supplied an entity" do
        before do
          d.retract(d.entity(entity_id))
        end

        it_behaves_like 'a retraction'
      end
    end
  end

  describe "#attribute" do
    let(:attribute) { d.attribute(:'db/doc') }
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


