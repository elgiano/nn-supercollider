#include "NNModelCmd.hpp"
#include "NNModel.hpp"
#include "SC_InterfaceTable.h"
#include "SC_PlugIn.hpp"
#include "SC_ReplyImpl.hpp"
#include "scsynthsend.h"

extern InterfaceTable* ft;
extern NN::NNModelDescLib gModels;

namespace NN::Cmd {

// defining async commands is complicated because of scsynth/nova implementations
// the common interface requires to use DefinePlugInCmd and DoAsynchronousCommand from ftTable
// - we need to pass osc args as void* inData
// - if we want to SendReply, we need to pass replyAddress to the actual stage function
template<class Cmd>
struct BaseAsyncCmd {
  ReplyAddress* mReplyAddr;
  sc_msg_iter* oscArgs;

  void SendFailure(const char* errString) {
    const char* cmdName = Cmd::cmdName();
    small_scpacket packet;
    packet.adds("/fail");
    packet.maketags(3);
    packet.addtag(',');
    packet.addtag('s');
    packet.addtag('s');
    packet.adds(cmdName);
    packet.adds(errString);

    Print("FAILURE IN SERVER: /cmd %s %s\n", cmdName, errString);
    SendReply(mReplyAddr, packet.data(), packet.size());
  }

  BaseAsyncCmd() = delete;
  static Cmd* alloc(sc_msg_iter* args, ReplyAddress* replyAddr, World* world=nullptr) {
    size_t oscDataSize = args->remain();
    size_t dataSize = sizeof(Cmd) + oscDataSize;
    // Print("allocating data size: %d\n", dataSize);

    Cmd* cmdData = (Cmd*) (world ? RTAlloc(world, dataSize) : NRTAlloc(dataSize));
    if (cmdData == nullptr) {
      Print("%s: msg data alloc failed.\n", Cmd::cmdName());
      return nullptr;
    }
    cmdData->mReplyAddr = replyAddr;
    cmdData->oscArgs = new (cmdData + 1) sc_msg_iter(oscDataSize, args->data + args->size - args->remain());

    return cmdData;
  }

  static void nrtFree(World*, void* data) { NRTFree(data); }

  static void asyncCmd(World* world, void* inUserData, sc_msg_iter* args, void* replyAddr) {
    const char* cmdName = Cmd::cmdName();
    // Print("nn async cmd name: %s\n", cmdName);
    Cmd* data = Cmd::alloc(args, static_cast<ReplyAddress*>(replyAddr), nullptr);
    if (data == nullptr) return;
    DoAsynchronousCommand(
      world, replyAddr, cmdName, data,
      Cmd::stage2, // stage2 is non real time
      Cmd::stage3, // stage3: RT (completion msg performed if true)
      Cmd::stage4, // stage4: NRT (sends /done if true)
      nrtFree, 0, 0);
  }

  static void define() {
    DefinePlugInCmd(Cmd::cmdName(), Cmd::asyncCmd, nullptr);
  }

  static bool stage2(World*, void*) { return true; }
  static bool stage3(World*, void*) { return true; }
  static bool stage4(World*, void*) { return true; }
};

struct NNLoadCmd : BaseAsyncCmd<NNLoadCmd> {

  static const char* cmdName() { return "/nn_load"; }

  static bool stage2(World* world, void* inData) {
    auto cmdData = (NNLoadCmd*) inData;
    auto args = cmdData->oscArgs;
    const int id = args->geti(-1);
    const char* path = args->gets();
    const char* filename = args->gets("");

    if (path == 0) {
      cmdData->SendFailure("needs a path to a .ts file");
      return false;
    }

    // Print("nn_load: idx %d path %s\n", id, path);
    auto model = (id == -1) ? gModels.load(path) : gModels.load(id, path);
    if (model == nullptr) {
      char errMsg[256]; sprintf(errMsg, "can't load model at %s", path);
      cmdData->SendFailure(errMsg);
      return false;
    }

    if (strlen(filename) > 0)
      model->dumpInfo(filename);
    return true;
  }
};

// /cmd /nn_query str
struct NNQueryCmd : BaseAsyncCmd<NNQueryCmd> {

  static const char* cmdName() { return "/nn_query"; }

  static bool stage2(World* world, void* inData) {
    auto cmdData = (NNQueryCmd*) inData;
    auto args = cmdData->oscArgs;
    const int modelIdx = args->geti(-1);
    const char* outFile = args->gets("");

    bool writeToFile = strlen(outFile) > 0;
    if (modelIdx < 0) {
      if (writeToFile) gModels.dumpAllInfo(outFile); else gModels.printAllInfo();
      return true;
    }
    const auto model = gModels.get(static_cast<unsigned short>(modelIdx), true);
    if (model) {
      if (writeToFile) model->dumpInfo(outFile); else model->printInfo();
    }
    return true;
  }
};

// /nn_unload i
struct NNUnloadCmd : BaseAsyncCmd<NNUnloadCmd> {
public:
  static const char* cmdName() { return "/nn_unload"; }

  static bool stage2(World* world, void* inData) {
    auto cmdData = (NNUnloadCmd*) inData;
    auto args = cmdData->oscArgs;
    int id = args->geti(-1);
    gModels.unload(id);
    return true;
  }
};


// // /cmd /nn_warmup int int
// struct NNWarmupCmd : BaseAsyncCmd<NNUnloadCmd> {
// public:
//   static const char* cmdName() { return "/nn_warmup"; }
//
//   static bool stage2(World* world, void* inData) {
//     auto cmdData = (NNWarmupCmd*) inData;
//     int modelIdx = cmdData->oscArgs->geti(-1);
//     int methodIdx = cmdData->oscArgs->geti(-1);
//
//    if (modelIdx < 0) {
//       // Print("nn_warmup: invalid model index %d\n", modelIdx);
//       const char errMsg[256];
//       sprintf(errMsg, "invalid model index %d", id);
//       cmdData->SendFailure(errMsg);
//       return true;
//    }
//    const auto model = gModels.get(static_cast<unsigned short>(modelIdx), true);
//    if (model) {
//      if (methodIdx < 0) {
//        // warmup all methods */
//        for(auto method: model->m_methods) model->warmup_method(&method);
//      } else {
//        auto method = model->getMethod(methodIdx, true);
//        if (method) model->warmup_method(method);
//      }
//    }
//    return true;
//   }
// };


void definePlugInCmds() {
  NNLoadCmd::define();
  NNUnloadCmd::define();
  NNQueryCmd::define();
  // NNWarmupCmd::define();
}

} // namespace NN::Cmd

