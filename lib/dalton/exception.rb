module Dalton
  class UniqueConflict < DatomicError
    # TODO: [jneen] this is terrible, but error handling is not implemented at the moment.
    # eventually all this data should be accessible via (ex-data e).
    MESSAGE_RE =
      %r(^:db[.]error/unique-conflict Unique conflict: :([a-z./-]+), value: (.*?) already held by: (\d+) asserted for: (\d+)$)o

    def self.parse(message)
      message =~ MESSAGE_RE
      raise ArgumentError, "invalid format: #{message.inspect}" unless $~
      new(
        attribute: $1.to_sym,
        value: $2,
        existing_id: Integer($3),
        new_id: Integer($4),
      )
    end

    attr_reader :message, :attribute, :value, :existing_id, :new_id

    def initialize(opts={})
      @attribute = opts.fetch(:attribute)
      @value = opts.fetch(:value)
      @existing_id = opts.fetch(:existing_id)
      @new_id = opts.fetch(:new_id)
      @message = "Unique conflict: tried to assign duplicate #@attribute to #@new_id, already held by #@existing_id. value: #@value"
    end

    def to_s
      "#{self.class.name}: #@message"
    end

    def inspect
      "#<#{self.class.name}: @attribute=#@attribute @value=#@value @existing_id=#@existing_id @new_id=#@new_id>"
    end
  end

  class TypeError < DatomicError
    MESSAGE_RE = %r(^:db[.]error/wrong-type-for-attribute Value (.*?) is not a valid :(\w+) for attribute :([a-z./-]+)$)

    def self.parse(message)
      message =~ MESSAGE_RE
      raise ArgumentError, "invalid format: #{message.inspect}" unless $~
      new(
        value: $1,
        type: $2.to_sym,
        attribute: $3.to_sym
      )
    end

    attr_reader :message, :value, :type, :attribute

    def initialize(opts={})
      @value = opts.fetch(:value)
      @type = opts.fetch(:type)
      @attribute = opts.fetch(:attribute)
      @message = "Type error: tried to set #@attribute as #@value, expected type #@type"
    end

    def to_s
      "#{self.class.name}: #@message"
    end

    def inspect
      "#<#{self.class.name}: @attribute=#@attribute @value=#@value @type=#@type>"
    end
  end
end
