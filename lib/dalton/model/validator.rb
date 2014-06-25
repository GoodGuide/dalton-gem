module Dalton
  module Model
    class ValidationError < StandardError
      attr_reader :changes, :errors

      def initialize(changes, errors)
        @changes = changes
        @errors = errors
      end

      def errors_on(key, &b)
        return enum_for(:errors_on, key).to_a unless block_given?

        errors.each do |(keys, message)|
          yield message if keys.include? key
        end
      end

      def errors_on?(key)
        errors_on(key).any?
      end
    end

    class Validator
      class Rule
        class Scope
          def initialize(attrs, validate, &report)
            @validate = validate
            @attrs = attrs
            @report = report
          end

          def invalid!(attr_names=nil, description)
            attr_names ||= @attrs
            attr_names = Array(attr_names)

            @report.call [attr_names, description]
          end

          def run(values)
            instance_exec(*values, &@validate)
          end
        end

        def initialize(*attrs, &block)
          @attrs = attrs
          @block = block
        end

        def run(changer, &out)
          values = @attrs.map { |a| changer.send(a) }
          Scope.new(@attrs, @block, &out).run(values)
        end
      end

      attr_reader :validators
      def initialize(model, &defn)
        @model = model
        @validators = []
        specify(&defn) if defn
      end

      def specify(&defn)
        instance_eval(&defn)
      end

      def validate(*attrs, &block)
        validators << Rule.new(*attrs, &block)
      end

      def run_all(changer, &report)
        return enum_for(:run_all, changer).to_a unless block_given?

        validators.each { |v| v.run(changer, &report) }
      end

      def run_all!(changer)
        errors = run_all(changer)
        raise ValidationError.new(changer, errors) if errors.any?
      end
    end
  end
end
