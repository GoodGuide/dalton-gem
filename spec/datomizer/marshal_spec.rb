require 'rspec'
require_relative('../../lib/datomizer')

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
                   :'db.install/_attribute' => :'db.part/db',
                  }])
    end

    context "with map values" do
      let(:value) {{:a => 'fnord'}}

      it "should store and retrieve map values" do
        pending "tomorrow"

        d.transact([{:'db/id' => Datomizer::Database.tempid,
                     :'test/stuff' => value
                    }])
        entity = d.retrieve(find: :'?e', where: [:'?e', :'db/stuff'])
        expect(entity[:'test/stuff']).to eql(value)
      end
    end

    context "with array values"
    context "with nested data structure values"

  end
end
