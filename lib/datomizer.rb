module Datomizer

  autoload :Database, File.join(File.dirname(__FILE__), 'datomizer', 'database')

end


# note: this needs to run *after* minitest's at_exit autorun handler. :-/
at_exit do
  Java::Datomic::Peer.shutdown(true)
end
