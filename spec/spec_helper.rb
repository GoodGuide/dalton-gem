require 'rspec'
require_relative('../lib/dalton')

Dir[File.join(File.dirname(__FILE__), 'support/**/*')].each {|f| require(f)}
