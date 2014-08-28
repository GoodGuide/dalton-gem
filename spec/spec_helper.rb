require 'bundler'
require 'jbundler'
Bundler.require

require 'wrong/adapters/rspec'

def reload!
  Object.send(:remove_const, :Dalton) if defined?(Dalton)
  load './lib/dalton.rb'

  Dalton::Model.logger = Logger.new('log/test.log')
  Dalton::Model.logger.level = Logger::DEBUG
  Dalton::Model.logger.info "**** Reloading Dalton ****"

  Dir[File.join(File.dirname(__FILE__), 'support/**/*.rb')].each {|f| load(f)}

  true
end

reload!

RSpec.configure do |config|
  config.backtrace_exclusion_patterns = []
  config.backtrace_inclusion_patterns = []
end
