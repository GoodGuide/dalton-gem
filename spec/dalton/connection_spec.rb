require 'spec_helper'

describe Dalton::Connection do
  include DatomicContext

  let(:attribute) { :'db/doc' }
  let(:value) { 'This is a test entity.' }

  let!(:transaction_result) { conn.transact([{:'db/id' => Dalton::Connection.tempid, attribute => value}]) }

  let(:entity_id) { transaction_result.tempids.values.first }

  describe 'data storage, query, and retrieval' do

    let(:query) { [:find, :'?e', :where, [:'?e', attribute, value]] }
    let(:edn_query) { '[:find ?e :where [?e :db/doc "This is a test entity."]]' }

    describe '#transact(datoms)' do

      it 'stores data' do
        expect(db.q(query).size).to eq(1)
      end

      it 'returns a transaction result' do
        expect(transaction_result.db_before).to be_a(Java::Datomic::Database)
        expect(transaction_result.db_after).to be_a(Java::Datomic::Database)
        expect(transaction_result.tx_data).to be_a(Array)
        expect(transaction_result.tempids).to be_a(Hash)
      end

      it 'refreshes the database' do
        expect(db.datomic_db).to equal(transaction_result.db_after)
      end

      describe 'errors' do
        before do
          # create an attribute with :db.unique/value
          conn.transact([{:'db/id' => Dalton::Connection.tempid(:'db.part/db'),
                       :'db/ident' => :'user.test/unique-attr',
                       :'db/cardinality' => :'db.cardinality/one',
                       :'db/unique' => :'db.unique/value',
                       :'db/valueType' => :'db.type/string',
                       :'db.install/_attribute' => :'db.part/db'}])

          tempid = Dalton::Connection.tempid(:'db.part/user')
          conn.transact([[:'db/add', tempid, :'user.test/unique-attr', 'duplicate-value']])
        end

        describe 'in uniqueness' do
          let(:error) {
            err = nil
            tempid = Dalton::Connection.tempid(:'db.part/user')
            begin
              conn.transact([[:'db/add', tempid, :'user.test/unique-attr', 'duplicate-value']])
            rescue Dalton::UniqueConflict => e
              err = e
            end
            err
          }

          it 'contains useful information' do
            expect(error).to be_a(Dalton::UniqueConflict)
            expect(error.attribute).to be(:'user.test/unique-attr')
            expect(error.value).to eql('duplicate-value')
            expect(error.existing_id).to be > 0
          end
        end

        describe 'in type' do
          let(:error) do
            err = nil
            tempid = Dalton::Connection.tempid(:'db.part/user')
            begin
              conn.transact([[:'db/add', tempid, :'user.test/unique-attr', 5]])
            rescue Dalton::TypeError => e
              err = e
            end
            err
          end

          it 'contains useful information' do
            expect(error).to be_a(Dalton::TypeError)
            expect(error.attribute).to be(:'user.test/unique-attr')
            expect(error.value).to eql('5') # TODO: this sucks, but they're indistinguishable!
            expect(error.type).to be(:string)
          end
        end
      end
    end

    describe '#retract(entity)' do
      shared_examples_for "a retraction" do
        it 'retracts the entity' do
          expect(conn.db.q(query).size).to eq(0)
        end
      end

      context "when supplied an id" do
        before do
          conn.retract(entity_id)
        end

        it_behaves_like 'a retraction'
      end

      context "when supplied an entity" do
        before do
          conn.retract(conn.db.entity(entity_id))
        end

        it_behaves_like 'a retraction'
      end
    end
  end

  describe '#tempid?' do
    subject {Dalton::Connection.tempid?(id)}

    context 'with a temp id' do
      let (:id) {Dalton::Connection.tempid}

      it { should be true }
    end

    context 'with a real id' do
      let (:id) {entity_id}

      it { should be false }
    end
  end
end


