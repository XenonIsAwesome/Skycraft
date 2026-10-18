#include "Dig.h"
#include "Game.h"

namespace
{
	void SetupLog()
	{
		auto dir = SKSE::log::log_directory();
		if (!dir) {
			return;
		}
		auto path = *dir / "SkyCraft.log";
		auto sink = std::make_shared<spdlog::sinks::basic_file_sink_mt>(path.string(), true);
		auto log = std::make_shared<spdlog::logger>("global", std::move(sink));
		log->set_level(spdlog::level::info);
		log->flush_on(spdlog::level::info);
		spdlog::set_default_logger(std::move(log));
		spdlog::set_pattern("[%H:%M:%S.%e] [%l] %v");
	}

	void OnMessage(SKSE::MessagingInterface::Message* a_msg)
	{
		switch (a_msg->type) {
		case SKSE::MessagingInterface::kDataLoaded:
			if (!skycraft::Link::Get().Create()) {
				logger::error("SkyCraft disabled: could not create shared memory");
				return;
			}
			skycraft::Game::Install();
			skycraft::Input::Install();
			skycraft::Overlay::Install();
			skycraft::WorldRender::Install();
			skycraft::PathAvoid::Install();
			skycraft::Dig::Install();
			skycraft::CrashLog::Install();
			break;
		case SKSE::MessagingInterface::kPostLoadGame:
		case SKSE::MessagingInterface::kNewGame:
			skycraft::Game::OnGameLoaded();
			break;
		default:
			break;
		}
	}
}

SKSEPluginLoad(const SKSE::LoadInterface* a_skse)
{
	SKSE::Init(a_skse, { .trampoline = true, .trampolineSize = 1024 });
	SetupLog();
	logger::info("SkyCraft 0.1.2 (built {} {}): SKSE::Init + log ok", __DATE__, __TIME__);
	try {
		skycraft::CrashLog::Install();
		logger::info("crash handler installed");
		logger::info("runtime {}", a_skse->RuntimeVersion().string());
		SKSE::GetMessagingInterface()->RegisterListener(OnMessage);
		logger::info("messaging listener registered; starting Minecraft launcher");
		// As early as possible: Minecraft takes about as long to start as Skyrim does to reach its menu.
		skycraft::Launcher::StartMinecraft();
		logger::info("load complete");
	} catch (const std::exception& e) {
		logger::critical("load failed: {}", e.what());
		return false;
	} catch (...) {
		logger::critical("load failed: unknown exception");
		return false;
	}
	return true;
}
