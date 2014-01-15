module Datomizer
  class Database

    def initialize(uri)
      @uri = uri
    end

    attr_reader :uri

  end
end
