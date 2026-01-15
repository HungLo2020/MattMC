package net.distanthorizons.core.network.messages;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import net.distanthorizons.core.network.messages.base.CodecCrashMessage;
import net.distanthorizons.core.network.messages.base.LevelInitMessage;
import net.distanthorizons.core.network.messages.base.SessionConfigMessage;
import net.distanthorizons.core.network.messages.fullData.FullDataSplitMessage;
import net.distanthorizons.core.network.messages.requests.CancelMessage;
import net.distanthorizons.core.network.messages.base.CloseReasonMessage;
import net.distanthorizons.core.network.messages.requests.ExceptionMessage;
import net.distanthorizons.core.network.messages.fullData.FullDataPartialUpdateMessage;
import net.distanthorizons.core.network.messages.fullData.FullDataSourceRequestMessage;
import net.distanthorizons.core.network.messages.fullData.FullDataSourceResponseMessage;
import net.distanthorizons.coreapi.ModInfo;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/** Keeps track of every {@link AbstractNetworkMessage} so they can be (De)serialized. */
public class MessageRegistry
{
	public static final boolean DEBUG_CODEC_CRASH_MESSAGE = ModInfo.IS_DEV_BUILD;
	public static final MessageRegistry INSTANCE = new MessageRegistry();
	
	private final Map<Integer, Supplier<? extends AbstractNetworkMessage>> messageConstructorById = new HashMap<>();
	private final BiMap<Class<? extends AbstractNetworkMessage>, Integer> messageClassById = HashBiMap.create();
	
	
	
	//=============//
	// constructor //
	//=============//
	
	private MessageRegistry()
	{
		// Note: Messages must have parameterless constructors
		
		this.registerMessage(CloseReasonMessage.class, CloseReasonMessage::new);
		
		// Level keys
		this.registerMessage(LevelInitMessage.class, LevelInitMessage::new);
		
		// Config (for full DH support)
		this.registerMessage(SessionConfigMessage.class, SessionConfigMessage::new);
		
		// Requests
		this.registerMessage(CancelMessage.class, CancelMessage::new);
		this.registerMessage(ExceptionMessage.class, ExceptionMessage::new);
		
		// Full data requests & updates
		this.registerMessage(FullDataSourceRequestMessage.class, FullDataSourceRequestMessage::new);
		this.registerMessage(FullDataSourceResponseMessage.class, FullDataSourceResponseMessage::new);
		this.registerMessage(FullDataPartialUpdateMessage.class, FullDataPartialUpdateMessage::new);
		this.registerMessage(FullDataSplitMessage.class, FullDataSplitMessage::new);
		
		// Debug messages are always last, and not included in release builds.
		if (DEBUG_CODEC_CRASH_MESSAGE)
		{
			this.registerMessage(CodecCrashMessage.class, CodecCrashMessage::new);
		}
	}
	
	
	
	//==================//
	// message handling //
	//==================//
	
	public <T extends AbstractNetworkMessage> void registerMessage(Class<T> clazz, Supplier<T> supplier)
	{
		int id = this.messageConstructorById.size() + 1;
		this.messageConstructorById.put(id, supplier);
		this.messageClassById.put(clazz, id);
	}
	
	/** used when decoding messages */
	public AbstractNetworkMessage createMessage(int messageId) throws IllegalArgumentException
	{
		try
		{
			return this.messageConstructorById.get(messageId).get();
		}
		catch (NullPointerException e)
		{
			throw new IllegalArgumentException("Invalid message ID: " + messageId);
		}
	}
	
	
	
	//=========//
	// getters //
	//=========//
	
	public int getMessageId(AbstractNetworkMessage message) { return this.getMessageId(message.getClass()); }
	public int getMessageId(Class<? extends AbstractNetworkMessage> messageClass)
	{
		try
		{
			return this.messageClassById.get(messageClass);
		}
		catch (NullPointerException e)
		{
			throw new IllegalArgumentException("Message does not have ID assigned to it: [" + messageClass.getSimpleName() + "].");
		}
	}
	
}