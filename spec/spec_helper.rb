require 'rspec'
require_relative('../lib/datomizer')

Dir[File.join(File.dirname(__FILE__), 'support/**/*')].each {|f| puts f; require f}
