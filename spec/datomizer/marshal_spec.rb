require 'spec_helper'

describe Datomizer::Marshal do

  let(:uri) { 'datomic:mem://spec' }
  let(:d) { Datomizer::Database.new(uri) }

  before do
    d.create
    d.connect
    d.refresh
  end

  after do
    d.destroy
  end


  describe 'data structure handling' do
    before do
      d.transact(Datomizer::Marshal::SCHEMA)
      d.transact([{:'db/id' => Datomizer::Database.tempid(':db.part/db'),
                   :'db/ident' => :'test/stuff',
                   :'db/valueType' => :'db.type/ref',
                   :'db/cardinality' => :'db.cardinality/one',
                   :'db/doc' => "A reference attribute for testing marshalling",
                   :'db/isComponent' => true,
                   :'db.install/_attribute' => :'db.part/db',
                  }])
    end

    context "with map values" do
      let(:value) {{:a => 'fnord'}}

      it "should store and retrieve map values" do
        d.transact([{:'db/id' => Datomizer::Database.tempid,
                     :'test/stuff' => Datomizer::Marshal.collection_to_datoms(value)
                    }])

        entities = d.retrieve([:find, :'?e', :where, [:'?e', :'test/stuff']])
        expect(entities.size).to eq(1)
        entity = entities.first

        entity_edn = Datomizer::Utility.to_edn(entity.datomic_entity.touch)

        data = Datomizer::Marshal.entity_to_data(entity)
        expect(data[:'test/stuff']).to eq(value)
      end
    end

    context "with array values"
    context "with nested data structure values"

  end
end
