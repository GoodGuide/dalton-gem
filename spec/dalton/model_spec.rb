require 'spec_helper'

describe Dalton::Model do
  Dalton::Model.configure do |m|
    m.namespace = :dalton
    m.partition = :spec
    m.uri = 'datomic:mem://spec'
  end

  class Sample
    include Dalton::Model

    schema do
      attribute "Foo attribute", :foo, :value_type => :string
      attribute "Bar attribute", :'bar-custom-key', :value_type => :string
      attribute "Parent model", :parent, :value_type => :ref
      attribute "Overrideable attribute", :overrideable, :value_type => :string
    end

    attribute :foo, :default => 'foo-default'
    attribute :bar, 'dalton.sample/bar-custom-key'
    attribute :overrideable
    attribute :parent
    referenced :children, :type => Sample, :from => :parent

    changers do
      def overrideable=(v)
        super
        self.foo = "overridden with #{v}"
      end
    end

    validation do
      validate :foo do |foo|
        if foo =~ /invalid/
          invalid! "must not contain the string 'invalid'"
        end
      end
    end
  end

  before do
    Dalton::Model.install_bases!
    Dalton::Model.install_schemas!
  end

  after do
    Sample.connection.destroy
  end

  describe 'basic model' do
    let(:model) do
      Sample.create! do |m|
        m.bar = 'bar-value'
      end
    end

    describe '.create!' do
      it 'creates a model' do
        assert { model.is_a? Sample }
        assert { model.foo == 'foo-default' }
        assert { model.bar == 'bar-value' }
      end
    end

    describe '#change' do
      it 'returns a new model with the changes' do
        next_model = model.change! do |m|
          m.foo = 'new-foo-value'
        end

        assert { next_model.is_a? Sample }
        assert { next_model.foo == 'new-foo-value' }
        assert { next_model.bar == 'bar-value' }
        assert { model.foo == 'foo-default' }
      end

      it 'allows `super` in overridden methods' do
        next_model = model.change! do |m|
          m.overrideable = 'x'
        end

        assert { next_model.overrideable == 'x' }
        assert { next_model.foo == 'overridden with x' }
      end
    end

    describe 'validations' do
      let(:validation_error) do
        rescuing {
          model.change! do |m|
            m.foo = 'invalid-foo-value'
          end
        }
      end

      it 'raises a validation error' do
        assert { validation_error.is_a? Dalton::Model::ValidationError }
        assert { validation_error.errors.length == 1 }
        assert { validation_error.errors_on(:foo) == ["must not contain the string 'invalid'"] }
        assert { validation_error.changes.change_in(:foo) == ['foo-default', 'invalid-foo-value'] }
      end
    end

    describe 'finders' do
      let(:finder) do
        model.finder
      end

      describe '#entity' do
        it 'returns the same model' do
          refreshed = finder.entity(model.id)
          assert { refreshed == model }
        end
      end

      describe '#by_{attribute}' do
        it 'returns the same model' do
          by_bar = finder.by_bar('bar-value')
          assert { by_bar.first == model }
        end
      end

      describe '#where' do
        it 'returns the same model' do
          by_bar = finder.where(:bar => 'bar-value')
          assert { by_bar.first == model }
        end
      end
    end

    describe 'relations' do
      it 'starts out nil/empty' do
        assert { model.parent == nil }
        assert { model.children == [] }
      end

      it 'sets a one-to-many relation' do
        next_model = model.change! do |m|
          m.parent = model
        end

        assert { next_model.parent == next_model }
        assert { next_model.children.to_a == [next_model] }
      end

      it 'sets a many-to-one relation' do
        next_model = model.change! do |m|
          m.children = [model]
        end

        assert { next_model.parent == next_model }
        assert { next_model.children.to_a == [next_model] }
      end

      it 'creates sub-entities' do
        next_model = model.change! do |m|
          m.parent = Sample.create { |p| p.foo = 'parent' }
          m.foo = 'child'
        end

        assert { next_model.parent.is_a? Sample }
        assert { next_model.parent.foo == 'parent' }
        assert { next_model.parent.id.is_a? Fixnum }
      end

      it 'creates sub-entities from reverse collections' do
        next_model = model.change! do |m|
          m.children = %w(a b c).map do |v|
            Sample.create { |c| c.foo = v }
          end
          m.foo = 'parent'
        end

        assert { next_model.children.count == 3 }
        assert { next_model.children.map(&:foo).to_a.sort == %w(a b c) }
      end
    end
  end
end


