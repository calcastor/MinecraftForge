package net.minecraftforge.server.console;

import java.io.IOException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import jline.console.ConsoleReader;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.util.EnumChatFormatting;

public final class TerminalHandler
{

    private static final Logger logger = LogManager.getLogger();

    private TerminalHandler()
    {
    }

    public static boolean handleCommands(DedicatedServer server)
    {
        final ConsoleReader reader = TerminalConsoleAppender.getReader();
        if (reader != null)
        {
            TerminalConsoleAppender.setFormatter(new ConsoleFormatter());
            reader.addCompleter(new ConsoleCommandCompleter(server));

            String line;
            while (!server.isServerStopped() && server.isServerRunning())
            {
                try
                {
                    line = reader.readLine("> ");
                    if (line == null)
                    {
                        break;
                    }

                    line = line.trim();
                    if (!line.isEmpty())
                    {
                        server.addPendingCommand(line, server);
                    }
                }
                catch (IOException e)
                {
                    logger.error("Exception handling console input", e);
                }
            }

            return true;
        }
        else
        {
            TerminalConsoleAppender.setFormatter(EnumChatFormatting::getTextWithoutFormattingCodes);
            return false;
        }
    }

}
