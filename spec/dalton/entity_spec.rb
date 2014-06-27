require 'spec_helper'

describe Dalton::Entity do
  include DatomicContext

  before do
    conn.transact([{:'db/id' => Dalton::Connection.tempid(':db.part/db'),
                 :'db/ident' => :'test/stuff',
                 :'db/valueType' => :'db.type/ref',
                 :'db/cardinality' => :'db.cardinality/one',
                 :'db/doc' => 'A reference attribute for testing datomization',
                 :'db/isComponent' => true,
                 :'db.install/_attribute' => :'db.part/db',
                }])
  end

  describe '#to_h' do
    let!(:transaction_result) {
      conn.transact([{:'db/id' => Dalton::Connection.tempid,
                   :'db/doc' => 'foo',
                   :'test/stuff' => {:'db/id' => Dalton::Connection.tempid,
                                     :'db/doc' => 'bar'}}
                 ])

    }
    let(:tempids) { transaction_result.tempids.values.sort }

    let(:entity) { conn.db.retrieve([:find, :'?e', :where, [:'?e', :'db/doc', 'foo']]).first }
    subject { entity.to_h }

    it 'should translate the entity to a hash' do
      expect(subject).to eq({:'db/doc' => 'foo',
                             :'test/stuff' =>
                               {:'db/doc' => 'bar'}})
    end
  end
end
