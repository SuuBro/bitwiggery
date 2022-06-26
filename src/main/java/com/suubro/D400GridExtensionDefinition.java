package com.suubro;
import java.util.UUID;

import com.bitwig.extension.api.PlatformType;
import com.bitwig.extension.controller.AutoDetectionMidiPortNamesList;
import com.bitwig.extension.controller.ControllerExtensionDefinition;
import com.bitwig.extension.controller.api.ControllerHost;

public class D400GridExtensionDefinition extends ControllerExtensionDefinition
{
   private static final UUID DRIVER_ID = UUID.fromString("d1205ed5-7c2e-4f3c-88eb-4f0299abc3ec");
   
   public D400GridExtensionDefinition()
   {
   }

   @Override
   public String getName()
   {
      return "D400Grid";
   }
   
   @Override
   public String getAuthor()
   {
      return "SuuBro";
   }

   @Override
   public String getVersion()
   {
      return "0.1";
   }

   @Override
   public UUID getId()
   {
      return DRIVER_ID;
   }
   
   @Override
   public String getHardwareVendor()
   {
      return "Multi";
   }
   
   @Override
   public String getHardwareModel()
   {
      return "D400Grid";
   }

   @Override
   public int getRequiredAPIVersion()
   {
      return 16;
   }

   @Override
   public int getNumMidiInPorts()
   {
      return 1;
   }

   @Override
   public int getNumMidiOutPorts()
   {
      return 1;
   }

   @Override
   public void listAutoDetectionMidiPortNames(final AutoDetectionMidiPortNamesList list, final PlatformType platformType)
   {
      list.add(new String[]{"D 400"}, new String[]{"D 400"});
   }

   @Override
   public D400GridExtension createInstance(final ControllerHost host)
   {
      return new D400GridExtension(this, host);
   }
}
