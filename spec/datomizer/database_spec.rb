require 'rspec'
require_relative('../../lib/datomizer/database')

describe Datomizer::Database do

  let(:uri){'datomic:mem://spec'}
  let(:db){Datomizer::Database.new(uri)}

  describe '.new' do
    it 'should set the url' do
      expect(db.uri).to equal(uri)
    end
  end

end


